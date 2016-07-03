/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.storage.datastream.Varint;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.LZFOutputStream;

public class PersistedIterable<T> implements Iterable<T>, AutoCloseable {

    private static final int DEFAULT_BUFFER_SIZE = 1000;

    private final Serializer<T> serializer;

    private @Nullable final Path tmpDir;

    private final int bufferSize;

    private final ArrayList<T> buffer;

    private final boolean compress;

    private volatile long size;

    private ReadWriteLock lock = new ReentrantReadWriteLock();

    Path tmpFile;

    public PersistedIterable(final @Nullable Path tmpDir, Serializer<T> serializer) {
        this(tmpDir, serializer, DEFAULT_BUFFER_SIZE, false);
    }

    public PersistedIterable(final @Nullable Path tmpDir, Serializer<T> serializer,
            final int bufferSize, final boolean compress) {
        checkNotNull(serializer);
        checkNotNull(bufferSize);
        checkArgument(bufferSize > 0, "bufferSize shall be > 0");
        this.serializer = serializer;
        this.tmpDir = tmpDir;
        this.bufferSize = bufferSize;
        this.buffer = new ArrayList<>(bufferSize);
        this.compress = compress;
    }

    public static PersistedIterable<String> newStringIterable(int bufferSize, boolean compress) {
        return newStringIterable(null, bufferSize, compress);
    }

    public static PersistedIterable<String> newStringIterable(@Nullable Path tmpDir, int bufferSize,
            boolean compress) {
        return new PersistedIterable<>(tmpDir, new StringSerializer(), bufferSize, compress);
    }

    @Override
    public void close() {
        lock.writeLock().lock();
        try {
            this.buffer.clear();
            if (this.tmpFile != null) {
                try {
                    Files.delete(this.tmpFile);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    this.tmpFile = null;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long size() {
        return this.size;
    }

    public void addAll(Iterable<T> collection) {
        for (T t : collection) {
            add(t);
        }
    };

    public void add(@NonNull T value) {
        checkNotNull(value);
        lock.writeLock().lock();
        try {
            this.buffer.add(value);
            this.size++;
            if (buffer.size() == bufferSize) {
                save();
                buffer.clear();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void save() {
        if (tmpFile == null) {
            createFile();
        }
        try (DataOutputStream out = createOutStream(tmpFile)) {
            for (T val : buffer) {
                int writtenBytes;
                writtenBytes = serializer.write(out, val);
            }
            out.flush();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private DataOutputStream createOutStream(Path tmpFile) throws IOException {
        OutputStream outStream = Files.newOutputStream(tmpFile, StandardOpenOption.APPEND);
        outStream = new BufferedOutputStream(outStream, 16 * 1024);
        if (this.compress) {
            outStream = new LZFOutputStream(outStream);
        }
        return new DataOutputStream(outStream);
    }

    private void createFile() {
        try {
            final String prefix = "geogigPersistedIterable";
            final String suffix = ".tmp";
            if (this.tmpDir == null) {
                this.tmpFile = Files.createTempFile(prefix, suffix);
            } else {
                this.tmpFile = Files.createTempFile(tmpDir, prefix, suffix);
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public Iterator<T> iterator() {

        Iterator<T> iterator = Collections.emptyIterator();

        lock.readLock().lock();
        try {
            final long streamLimit = this.tmpFile == null ? 0L
                    : (Files.exists(this.tmpFile) ? Files.size(this.tmpFile) : 0L);

            if (streamLimit > 0) {
                InputStream in;
                try {
                    in = Files.newInputStream(tmpFile, StandardOpenOption.READ);
                    in = new BufferedInputStream(in, 16 * 1024);
                    in = ByteStreams.limit(in, streamLimit);
                    if (this.compress) {
                        in = new LZFInputStream(in);
                    }
                    DataInputStream dataIn = new DataInputStream(in);
                    StreamIterator<T> streamIt = new StreamIterator<T>(serializer, dataIn);
                    iterator = Iterators.concat(iterator, streamIt);
                } catch (IOException e) {
                    throw Throwables.propagate(e);
                }
            }

            if (!this.buffer.isEmpty()) {
                ArrayList<T> buffered = Lists.newArrayList(this.buffer);
                iterator = Iterators.concat(iterator, buffered.iterator());
            }
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            lock.readLock().unlock();
        }

        return iterator;
    }

    private static class StreamIterator<T> extends AbstractIterator<T> {

        private final Serializer<T> serializer;

        private final DataInputStream in;

        public StreamIterator(Serializer<T> serializer, DataInputStream in) {
            this.serializer = serializer;
            this.in = in;
        }

        @Override
        protected T computeNext() {
            try {
                T read = serializer.read(in);
                return read;
            } catch (EOFException eof) {
                return endOfData();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }

    }

    public static interface Serializer<T> {

        /**
         * @return number of bytes written
         */
        public int write(DataOutputStream out, T value) throws IOException;

        /**
         * @param in
         * @return
         * @throws IOException
         * @throws EOFException if there are no more bytes to read
         */
        public T read(DataInputStream in) throws IOException;
    }

    /**
     * A serializer for Strings, admits {@code null}.
     */
    public static class StringSerializer implements Serializer<String> {

        private final Lock lock = new ReentrantLock();

        private byte[] buffer = new byte[4096];

        @Override
        public int write(DataOutputStream out, @Nullable String value) throws IOException {
            if (value == null) {
                return Varint.writeSignedVarInt(-1, out);
            }
            byte[] bytes = value.getBytes(Charsets.UTF_8);
            int length = bytes.length;
            length += Varint.writeSignedVarInt(length, out);
            out.write(bytes);
            return length;
        }

        @Nullable
        @Override
        public String read(DataInputStream in) throws IOException {
            final int length = Varint.readSignedVarInt(in);
            if (-1 == length) {
                return null;
            }
            lock.lock();
            try {
                ensureCapacity(length);
                in.readFully(buffer, 0, length);
                String s = new String(buffer, 0, length, Charsets.UTF_8);
                return s;
            } finally {
                lock.unlock();
            }
        }

        private void ensureCapacity(int length) {
            if (this.buffer.length < length) {
                int len = 512 + (length % 512) * 512;
                this.buffer = new byte[len];
            }
        }
    }
}
