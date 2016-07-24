/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import static com.google.common.base.Preconditions.checkState;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.storage.datastream.FormatCommonV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.primitives.UnsignedLong;
import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.LZFOutputStream;

import sun.misc.Cleaner;
import sun.nio.ch.DirectBuffer;

/**
 * A {@link NodeIndex} that saves nodes to a set of temporary files based on a threshold and returns
 * a mergesorted {@link #nodes() iterator} that provides a good enough sorting (based on node's
 * {@link CanonicalNodeNameOrder#hashCodeLong unsigned long hash}) to alleviate the work of the
 * {@link RevTreeBuilder}.
 *
 */
@SuppressWarnings("restriction")
class FileNodeIndex implements Closeable, NodeIndex {

    private static final Logger LOG = LoggerFactory.getLogger(FileNodeIndex.class);

    private static final CanonicalNodeNameOrder PATH_STORAGE_ORDER = CanonicalNodeNameOrder.INSTANCE;

    private static final CanonicalNodeOrder NODE_STORAGE_ORDER = new CanonicalNodeOrder();

    private static final Random random = new Random();

    private IndexPartition currPartition;

    private List<Future<File>> indexFiles = new LinkedList<Future<File>>();

    private List<CompositeNodeIterator> openIterators = new LinkedList<CompositeNodeIterator>();

    private ExecutorService executorService;

    private File tmpFolder;

    public FileNodeIndex(Platform platform, ExecutorService executorService) {
        File tmpFolder = new File(platform.getTempDir(), "nodeindex" + Math.abs(random.nextInt()));
        checkState(tmpFolder.mkdirs());
        this.tmpFolder = tmpFolder;
        this.executorService = executorService;
        this.currPartition = new OffHeapIndexPartition(this.tmpFolder);
    }

    @Override
    public void close() {
        try {
            for (CompositeNodeIterator it : openIterators) {
                it.close();
            }
        } finally {
            tmpFolder.delete();
            openIterators.clear();
            indexFiles.clear();
        }
    }

    @Override
    public synchronized void add(Node node) {
        if (!currPartition.add(node)) {
            flush(currPartition);
            currPartition = new OffHeapIndexPartition(this.tmpFolder);
            currPartition.add(node);
        }
    }

    private void flush(final IndexPartition ip) {
        indexFiles.add(executorService.submit(new Callable<File>() {

            @Override
            public File call() throws Exception {
                return ip.flush();
            }
        }));

    }

    @Override
    public Iterator<Node> nodes() {
        List<File> files = new ArrayList<File>(indexFiles.size());
        try {
            for (Future<File> ff : indexFiles) {
                files.add(ff.get());
            }
        } catch (Exception e) {
            e.printStackTrace();
            close();
            throw Throwables.propagate(Throwables.getRootCause(e));
        }

        // files.add(currPartition.flush());
        AutoCloseableIterator<Node> unflushed = currPartition.iterator();
        return new CompositeNodeIterator(files, unflushed);
    }

    private static class CompositeNodeIterator extends AbstractIterator<Node>
            implements AutoCloseableIterator<Node> {

        private List<AutoCloseableIterator<Node>> openIterators;

        private UnmodifiableIterator<Node> delegate;

        public CompositeNodeIterator(List<File> files,
                AutoCloseableIterator<Node> unflushedAndSorted) {

            openIterators = new ArrayList<>(files.size() + 1);
            for (File f : files) {
                AutoCloseableIterator<Node> iterator = new IndexIterator(f);
                openIterators.add(iterator);
            }
            if (unflushedAndSorted != null) {
                openIterators.add(unflushedAndSorted);
            }
            delegate = Iterators.mergeSorted(openIterators, NODE_STORAGE_ORDER);
        }

        @Override
        public void close() {
            for (AutoCloseableIterator<Node> it : openIterators) {
                it.close();
            }
            openIterators.clear();
        }

        @Override
        protected Node computeNext() {
            if (delegate.hasNext()) {
                return delegate.next();
            }
            return endOfData();
        }

    }

    private static class IndexIterator extends AbstractIterator<Node>
            implements AutoCloseableIterator<Node> {

        private DataInputStream in;

        private final File file;

        public IndexIterator(final File file) {
            this.file = file;
            Preconditions.checkArgument(file.exists(), "file %s does not exist", file);
            try {
                if (this.in == null) {
                    InputStream fin = new BufferedInputStream(new FileInputStream(file), 64 * 1024);
                    fin = new LZFInputStream(fin);
                    this.in = new DataInputStream(fin);
                }

            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }

        @Override
        public void close() {
            Closeables.closeQuietly(in);
            file.delete();
        }

        @Override
        protected Node computeNext() {
            try {
                Node node = FormatCommonV2.readNode(in);
                return node;
            } catch (EOFException eof) {
                close();
                return endOfData();
            } catch (Exception e) {
                close();
                throw Throwables.propagate(e);
            }
        }

    }

    static interface AutoCloseableIterator<N> extends Iterator<N>, AutoCloseable {
        @Override
        void close();
    }

    interface IndexPartition extends Iterable<Node> {

        /**
         * @return {@code true} if the node was added to the buffer, {@code false} if adding the
         *         node would exceed the buffer's capacity and hence a new partition needs to be
         *         created and this one shall be {@link #flush() flushed}
         */
        public boolean add(Node node);

        public File flush();

        public void clear();

        @Override
        public AutoCloseableIterator<Node> iterator();

    }

    /**
     * Holds actual node data in an off-heap {@link ByteBuffer} and an index of node order based on
     * Node's storage order in heap memory, until {@link #flush() flushed} to disk, then both off
     * heap cache and heap index get released.
     *
     */
    private static class OffHeapIndexPartition implements IndexPartition, Iterable<Node> {

        private File tmpFolder;

        private ByteBuffer buffer;

        private static final class Entry implements Comparable<Entry> {

            private final UnsignedLong nodeHash;

            private final int offset, length;

            Entry(UnsignedLong nodeHash, int offset, int length) {
                this.nodeHash = nodeHash;
                this.offset = offset;
                this.length = length;
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof Entry)) {
                    return false;
                }
                Entry e = (Entry) o;
                return nodeHash.equals(e.nodeHash) && offset == e.offset && length == e.length;
            }

            @Override
            public int compareTo(Entry o) {
                return nodeHash.compareTo(o.nodeHash);
            }
        }

        private SortedSet<Entry> positionIndex = new TreeSet<>();

        public OffHeapIndexPartition(final File tmpFolder) {
            this.tmpFolder = tmpFolder;
            final int maxBufferCapacity = figureOutBufferCapacity();
            buffer = ByteBuffer.allocateDirect(maxBufferCapacity);
        }

        private int figureOutBufferCapacity() {
            final long maxMemory = Runtime.getRuntime().maxMemory();
            if (maxMemory <= 512 * 1024 * 1024) {
                return 32 * 1024 * 1024;
            }
            if (maxMemory <= 1024 * 1024 * 1024) {
                return 64 * 1024 * 1024;
            }
            if (maxMemory <= 2048 * 1024 * 1024) {
                return 128 * 1024 * 1024;
            }
            return 256 * 1024 * 1024;
        }

        @Override
        public synchronized boolean add(Node node) {
            final UnsignedLong nodeHashKey = PATH_STORAGE_ORDER.hashCodeLong(node.getName());
            final int[] offsetLengh = encode(node);
            if (offsetLengh == null) {
                LOG.debug(String.format("reached max capacicy %,d at %,d nodes", buffer.position(),
                        positionIndex.size()));
                return false;
            }
            Entry e = new Entry(nodeHashKey, offsetLengh[0], offsetLengh[1]);
            positionIndex.add(e);
            return true;
        }

        private int[] encode(Node node) {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            try {
                FormatCommonV2.writeNode(node, out);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
            byte[] bytes = out.toByteArray();
            final int offset = buffer.position();
            final int length = bytes.length;
            if (offset + length > buffer.limit()) {
                return null;
            }
            ByteBuffer buffer = this.buffer;
            buffer.put(bytes, 0, length);
            return new int[] { offset, length };
        }

        private static class NodeReader implements Function<Entry, Node> {

            private ByteBuffer buffer;

            private byte[] buff = new byte[1024];

            public NodeReader(ByteBuffer buffer) {
                this.buffer = buffer;
            }

            @Override
            public Node apply(Entry e) {
                final int offset = e.offset;
                final int length = e.length;
                buff = ensureCapacity(buff, length);
                buffer.position(offset);
                buffer.get(buff, 0, length);

                ByteArrayDataInput in = ByteStreams.newDataInput(buff);
                Node node;
                try {
                    node = FormatCommonV2.readNode(in);
                } catch (IOException ex) {
                    throw Throwables.propagate(ex);
                }
                return node;
            }

        }

        private class NodeIterator extends AbstractIterator<Node>
                implements AutoCloseableIterator<Node> {

            private Iterator<Node> nodes = Iterators.transform(positionIndex.iterator(),
                    new NodeReader(buffer));

            @Override
            protected Node computeNext() {
                if (nodes.hasNext()) {
                    return nodes.next();
                }
                close();
                return endOfData();
            }

            @Override
            public void close() {
                OffHeapIndexPartition.this.clear();
            }
        }

        @Override
        public AutoCloseableIterator<Node> iterator() {
            return new NodeIterator();
        }

        @Override
        public File flush() {
            final File file;
            try {
                file = File.createTempFile("geogigNodes", ".idx", tmpFolder);
                file.deleteOnExit();
                LOG.trace("Created index file {}", file.getName());

                final ByteBuffer buffer = this.buffer;
                Stopwatch sw = Stopwatch.createStarted();
                byte[] buff = new byte[1024];
                int count = 0;
                try (OutputStream fileOut = new LZFOutputStream(
                        new BufferedOutputStream(new FileOutputStream(file), 1024 * 1024))) {

                    for (Entry e : positionIndex) {
                        final int offset = e.offset;
                        final int length = e.length;
                        buff = ensureCapacity(buff, length);
                        buffer.position(offset);
                        buffer.get(buff, 0, length);
                        fileOut.write(buff, 0, length);
                        count++;
                    }
                } finally {
                    sw.stop();
                    clear();
                    double fileSize = file.length() / 1024d / 1024d;
                    LOG.debug(String.format("Dumped %,d nodes to %s (%.2fMB) in %s", count,
                            file.getName(), fileSize, sw));
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw Throwables.propagate(e);
            }
            return file;
        }

        private static byte[] ensureCapacity(byte[] buff, int length) {
            if (buff.length >= length) {
                return buff;
            }
            return new byte[length];
        }

        @Override
        public void clear() {
            if (positionIndex != null) {
                positionIndex.clear();
                positionIndex = null;

                ByteBuffer buffer = this.buffer;
                this.buffer = null;
                if (buffer instanceof DirectBuffer) {
                    Cleaner cleaner = ((DirectBuffer) buffer).cleaner();
                    if (cleaner != null) {
                        cleaner.clean();
                    }
                }
            }
        }
    }

}
