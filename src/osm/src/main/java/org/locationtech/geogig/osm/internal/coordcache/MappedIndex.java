/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal.coordcache;

import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.RandomAccess;
import java.util.TreeMap;

import javax.annotation.Nullable;

import org.locationtech.geogig.osm.internal.OSMCoordinateSequence;
import org.locationtech.geogig.osm.internal.OSMCoordinateSequenceFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;
import com.google.common.primitives.Longs;

class MappedIndex {

    private static class Entry implements Comparable<MappedIndex.Entry> {

        public static final int RECSIZE = 16;// sizeOf(long) + 2 * sizeOf(int)

        public final long nodeId;

        public final int x;

        public final int y;

        public Entry(long nodeId, int[] coordinate) {
            this(nodeId, coordinate[0], coordinate[1]);
        }

        public Entry(long nodeId, int x, int y) {
            this.nodeId = nodeId;
            this.x = x;
            this.y = y;
        }

        @Override
        public int compareTo(MappedIndex.Entry o) {
            return Longs.compare(nodeId, o.nodeId);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;
            if (!(o instanceof MappedIndex.Entry)) {
                return false;
            }
            MappedIndex.Entry e = (MappedIndex.Entry) o;
            return nodeId == e.nodeId && x == e.x && y == e.y;
        }

        @Override
        public String toString() {
            return new StringBuilder("Entry[node: ").append(nodeId).append(", x: ").append(x)
                    .append(", y: ").append(y).append(']').toString();
        }

        public static void write(ByteBuffer buffer, MappedIndex.Entry entry) {
            if (buffer.remaining() < Entry.RECSIZE) {
                throw new BufferOverflowException();
            }
            buffer.putLong(entry.nodeId);
            buffer.putInt(entry.x);
            buffer.putInt(entry.y);
        }

        public static MappedIndex.Entry read(ByteBuffer buffer) {
            long nodeId = buffer.getLong();
            int x = buffer.getInt();
            int y = buffer.getInt();
            return new Entry(nodeId, x, y);
        }
    }

    private static final int MAX_ENTRIES_PER_BUFFER = 250 * 1000;

    private static final long MAX_BUFF_SIZE = MAX_ENTRIES_PER_BUFFER * MappedIndex.Entry.RECSIZE;

    private static final OSMCoordinateSequenceFactory CSFAC = new OSMCoordinateSequenceFactory();

    private File indexFile;

    private RandomAccessFile randomAccessFile;

    private FileChannel indexChannel;

    private BufferRange currBuffer;

    private List<BufferRange> ranges = new ArrayList<BufferRange>();

    public MappedIndex(final File parentDir) throws IOException {
        this.indexFile = new File(parentDir, "coordinates.idx");
        this.indexFile.deleteOnExit();
        checkState(this.indexFile.createNewFile(), "unable to create index file");

        randomAccessFile = new RandomAccessFile(indexFile, "rw");
        this.indexChannel = randomAccessFile.getChannel();
        this.ranges = new ArrayList<MappedIndex.BufferRange>(2);
        newBuffer();
    }

    private void newBuffer() throws IOException {
        long position = MAX_BUFF_SIZE * ranges.size();
        long size = MAX_BUFF_SIZE;
        MappedByteBuffer buff = indexChannel.map(MapMode.READ_WRITE, position, size);
        BufferRange range = new BufferRange(buff);
        ranges.add(range);
        this.currBuffer = range;
    }

    public void close() {
        try {
            Closeables.close(indexChannel, true);
            Closeables.close(randomAccessFile, true);
        } catch (IOException e) {
            //
        }
        currBuffer = null;
        indexChannel = null;
        indexFile.delete();
    }

    public void putCoordinate(final long nodeId, int[] coordinate) {
        Entry entry = new Entry(nodeId, coordinate);
        try {
            currBuffer.put(entry);
        } catch (BufferOverflowException needsNewBuffer) {
            try {
                newBuffer();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
            currBuffer.put(entry);
        }
    }

    private static class BufferRange {

        private int size = 0;

        private long minId = Long.MAX_VALUE, maxId = Long.MIN_VALUE;

        private TreeMap<Long, Entry> unsavedEntries = Maps.newTreeMap();

        public final ByteBuffer buffer;

        public BufferRange(ByteBuffer viewBuff) {
            this.buffer = viewBuff;
        }

        private synchronized void put(MappedIndex.Entry entry) {
            if (unsavedEntries.size() == MAX_ENTRIES_PER_BUFFER) {
                save();
                throw new BufferOverflowException();
            }
            long nodeId = entry.nodeId;
            unsavedEntries.put(Long.valueOf(nodeId), entry);
            minId = Math.min(minId, nodeId);
            maxId = Math.max(maxId, nodeId);
            size++;
        }

        private void save() {
            for (Entry e : unsavedEntries.values()) {
                Entry.write(buffer, e);
            }
            unsavedEntries.clear();
        }

        public boolean mayContain(Long nodeId) {
            return nodeId >= minId && nodeId <= maxId;
        }

        @Override
        public String toString() {
            return new StringBuilder("Nodes (").append(size).append(")[").append(minId)
                    .append("..").append(maxId).append(']').toString();
        }

        @Nullable
        public MappedIndex.Entry search(final Long nodeId) {
            if (unsavedEntries.isEmpty() && size > 0) {
                ByteBuffer view = buffer.duplicate();
                view.flip();
                List<MappedIndex.Entry> list = new EntryList(view);

                final long id = nodeId.longValue();
                final MappedIndex.Entry key = new Entry(id, 0, 0);
                final int index = Collections.binarySearch(list, key);
                if (index > -1) {
                    MappedIndex.Entry found = list.get(index);
                    return found;
                }
                return null;
            }
            Entry entry = unsavedEntries.get(nodeId);
            return entry;
        }
    }

    private static final class EntryList extends AbstractList<MappedIndex.Entry> implements
            RandomAccess {

        private ByteBuffer buffer;

        public EntryList(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public MappedIndex.Entry get(int index) {
            int offset = index * Entry.RECSIZE;
            buffer.position(offset);
            MappedIndex.Entry entry = Entry.read(buffer);
            return entry;
        }

        @Override
        public MappedIndex.Entry set(int index, MappedIndex.Entry element) {
            ByteBuffer buffer = this.buffer;
            final int offset = index * Entry.RECSIZE;
            buffer.position(offset);
            // MappedIndex.Entry prev = serializer.read(buffer);
            // buffer.position(offset);
            Entry.write(buffer, element);
            // return prev;
            return null;
        }

        @Override
        public int size() {
            int limit = buffer.limit();
            int size = limit / MappedIndex.Entry.RECSIZE;
            return size;
        }

    }

    public OSMCoordinateSequence build(List<Long> ids) {
        // sort();

        OSMCoordinateSequence sequence = CSFAC.create(ids.size());
        int[] coordinateBuff = new int[2];

        for (int index = 0; index < ids.size(); index++) {
            Long nodeId = ids.get(index);
            getCoordinate(nodeId, coordinateBuff);
            sequence.setOrdinate(index, 0, coordinateBuff[0]);
            sequence.setOrdinate(index, 1, coordinateBuff[1]);
        }

        return sequence;
    }

    private void getCoordinate(Long nodeId, int[] coordinateBuff) throws IllegalArgumentException {

        // Search ranges backwards to catch the latest version of a coordinate in case the same one
        // was added to more than one range
        for (int i = ranges.size() - 1; i >= 0; i--) {
            MappedIndex.BufferRange br = ranges.get(i);
            if (br.mayContain(nodeId)) {
                MappedIndex.Entry entry = br.search(nodeId);
                if (entry != null) {
                    coordinateBuff[0] = entry.x;
                    coordinateBuff[1] = entry.y;
                    return;
                }
            }
        }
        throw new IllegalArgumentException("Node #" + nodeId + " not found");
    }

}