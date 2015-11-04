/* Copyright (c) 2015 Boundless and others.
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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.osm.internal.OSMCoordinateSequence;
import org.locationtech.geogig.osm.internal.OSMCoordinateSequenceFactory;
import org.locationtech.geogig.storage.datastream.Varint;
import org.locationtech.geogig.storage.datastream.Varints;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.CoordinateSequence;

public class MapdbPointCache implements PointCache {

    private static final Random RANDOM = new Random();

    private static final OSMCoordinateSequenceFactory CSFAC = new OSMCoordinateSequenceFactory();

    private static final Serializer<Long> KEY_SERIALIZER = new Serializer<Long>() {

        @Override
        public void serialize(DataOutput out, Long value) throws IOException {
            Varint.writeUnsignedVarLong(value.longValue(), out);
        }

        @Override
        public Long deserialize(DataInput in, int available) throws IOException {
            return Varint.readUnsignedVarLong(in);
        }
    };

    private static final Serializer<Batch> VALUE_SERIALIZER = new Serializer<MapdbPointCache.Batch>() {

        @Override
        public void serialize(DataOutput out, Batch value) throws IOException {
            final int length = value.length;
            final long[] nodeIds = value.nodeIds;
            final int[] xordinates = value.xordinates;
            final int[] yordinates = value.yordinates;

            Varint.writeUnsignedVarInt(length, out);

            Varints.writeLongArrayDeltaEncoded(out, nodeIds, 0, length);
            Varints.writeIntArrayDeltaEncoded(out, xordinates, 0, length);
            Varints.writeIntArrayDeltaEncoded(out, yordinates, 0, length);
        }

        @Override
        public Batch deserialize(DataInput in, int available) throws IOException {
            final int length = Varint.readUnsignedVarInt(in);
            long[] nodeIds = new long[length + 1];
            int[] xordinates = new int[length + 1];
            int[] yordinates = new int[length + 1];

            Varints.readLongArrayDeltaEncoded(in, nodeIds, 0, length);
            Varints.readIntArrayDeltaEncoded(in, xordinates, 0, length);
            Varints.readIntArrayDeltaEncoded(in, yordinates, 0, length);

            Batch batch = new Batch(length, nodeIds, xordinates, yordinates);
            return batch;
        }
    };

    private final File parentDir;

    private final File dbFile;

    private final HTreeMap<Long, Batch> pointMap;

    public MapdbPointCache(Platform platform) {
        final File tmpDir = platform.getTempDir();
        checkState(tmpDir != null && tmpDir.isDirectory());
        synchronized (RANDOM) {
            this.parentDir = new File(tmpDir, "osmPointCache_" + Math.abs(RANDOM.nextInt()));
        }
        checkState(parentDir.exists() || parentDir.mkdirs());
        this.parentDir.deleteOnExit();

        this.dbFile = new File(parentDir, "temp"); // File.createTempFile("osmNodeCache", ".mapdb",
                                                   // parentDir);

        pointMap = DBMaker.fileDB(dbFile)//
                .deleteFilesAfterClose()//
                .closeOnJvmShutdown()//
                .transactionDisable()//
                .lockDisable()//
                .fileMmapEnableIfSupported()//
                .allocateIncrement(32 * 1024 * 1024)//
                .asyncWriteEnable()//
                .asyncWriteFlushDelay(1_000)//
                .make()//

                .hashMapCreate("temp")//
                .keySerializer(KEY_SERIALIZER)//
                .valueSerializer(VALUE_SERIALIZER)//
                .make();

    }

    @Override
    public void put(Long nodeId, OSMCoordinateSequence coord) {
        Preconditions.checkNotNull(nodeId, "id is null");
        Preconditions.checkNotNull(coord, "coord is null");
        Preconditions.checkArgument(1 == coord.size(), "coord list size is not 1");

        int[] ordinates = coord.ordinates();

        Long key = keyFor(nodeId);
        Batch e = getOrCreate(key);
        e.add(nodeId, ordinates);
        pointMap.put(key, e);
    }

    private Batch getOrCreate(Long key) {
        Batch batch = pointMap.get(key);
        if (batch == null) {
            batch = new Batch();
        }
        batch.setKey(key);
        return batch;
    }

    private Long keyFor(Long nodeId) {
        return nodeId.longValue() / 1_000;
    }

    @Override
    public CoordinateSequence get(List<Long> ids) {
        Preconditions.checkNotNull(ids, "ids is null");

        OSMCoordinateSequence sequence = CSFAC.create(ids.size());

        int[] coordinateBuff = new int[2];

        Batch batch = null;

        for (int index = 0; index < ids.size(); index++) {
            Long nodeId = ids.get(index);
            Long key = keyFor(nodeId);
            if (batch == null || !batch.key.equals(key)) {
                batch = getOrCreate(key);
            }

            batch.get(nodeId, coordinateBuff);

            sequence.setOrdinate(index, 0, coordinateBuff[0]);
            sequence.setOrdinate(index, 1, coordinateBuff[1]);
        }

        return sequence;
    }

    @Override
    public void dispose() {
        // System.out.println("size: " + pointMap.size());
        pointMap.close();
    }

    private static class Batch {

        transient int length;

        long[] nodeIds;

        int[] xordinates;

        int[] yordinates;

        private Long key;

        public Batch() {
            this(0, new long[2], new int[2], new int[2]);
        }

        public void setKey(Long key) {
            this.key = key;
        }

        public Batch(int length, long[] nodeIds, int[] xordinates, int[] yordinates) {
            Preconditions.checkArgument(length <= nodeIds.length);
            Preconditions.checkArgument(nodeIds.length == xordinates.length);
            Preconditions.checkArgument(nodeIds.length == yordinates.length);
            this.length = length;
            this.nodeIds = nodeIds;
            this.xordinates = xordinates;
            this.yordinates = yordinates;
        }

        public void add(Long nodeId, int[] coordinate) {
            final long id = nodeId.longValue();

            final int oldLength = this.length;
            // make sure there's room for a new value
            expand();

            // in-place insertion sort
            // Remember: Arrays.binarySearch() returns index of the search key, if it is contained
            // in the array; otherwise, (-(insertion point) - 1)
            final int insertionIndex;
            {
                final int insertonPoint = Arrays.binarySearch(nodeIds, 0, oldLength, id);
                insertionIndex = insertonPoint < 0 ? (-1 * insertonPoint) - 1 : insertonPoint;
            }
            if (insertionIndex < oldLength) {
                for (int i = oldLength; i > insertionIndex; i--) {
                    nodeIds[i] = nodeIds[i - 1];
                    xordinates[i] = xordinates[i - 1];
                    yordinates[i] = yordinates[i - 1];
                }
            }
            nodeIds[insertionIndex] = id;
            xordinates[insertionIndex] = coordinate[0];
            yordinates[insertionIndex] = coordinate[1];
        }

        private int expand() {
            final int length = this.length;
            final int newLength = length + 1;

            if (nodeIds.length < newLength) {
                long[] ids = new long[newLength + 10];
                int[] x = new int[newLength + 10];
                int[] y = new int[newLength + 10];

                System.arraycopy(nodeIds, 0, ids, 0, length);
                System.arraycopy(xordinates, 0, x, 0, length);
                System.arraycopy(yordinates, 0, y, 0, length);

                nodeIds = ids;
                xordinates = x;
                yordinates = y;
            }
            this.length = newLength;

            return newLength;
        }

        public void get(Long nodeId, int[] coordinateBuff) throws IllegalArgumentException {

            final int index = Arrays.binarySearch(nodeIds, 0, this.length, nodeId.longValue());
            if (index < 0) {
                throw new IllegalArgumentException("Node #" + nodeId + " not found at " + this);
            }
            coordinateBuff[0] = xordinates[index];
            coordinateBuff[1] = yordinates[index];
        }

        @Override
        public String toString() {
            return new StringBuilder("Batch[key:").append(key).append(", length:")
                    .append(this.length).append(", ids:").append(Arrays.toString(nodeIds))
                    .append("]").toString();
        }
    }

}
