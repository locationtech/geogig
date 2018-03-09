/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_3;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.ObjectId;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedMap.Builder;
import com.vividsolutions.jts.geom.Envelope;

class BucketSet {

    static final BucketSet EMPTY = new BucketSet(0, null, 0) {
        @Override
        public ImmutableSortedMap<Integer, Bucket> build() {
            return ImmutableSortedMap.of();
        }
    };

    private static final int HEADER_SIZE = Integer.BYTES;

    private static final int REC_SIZE = 1 + ObjectId.NUM_BYTES + 4 * 8;

    public static void encode(DataOutput out, SortedMap<Integer, Bucket> buckets,
            StringTable stringTable) throws IOException {

        final int size = buckets.size();
        out.writeInt(size);
        if (0 == size) {
            return;
        }

        checkArgument(128 >= buckets.lastKey().intValue(), "bucket index can't exceed 127");

        for (Map.Entry<Integer, Bucket> e : buckets.entrySet()) {
            Integer bucketIndex = e.getKey();
            Bucket bucket = e.getValue();
            ObjectId objectId = bucket.getObjectId();

            out.writeByte(bucketIndex.intValue());
            objectId.writeTo(out);

            Envelope bounds = bucket.bounds().orNull();
            if (bounds == null || bounds.isNull()) {
                out.writeDouble(Double.NaN);
            } else {
                out.writeDouble(bounds.getMinX());
                out.writeDouble(bounds.getMaxX());
                out.writeDouble(bounds.getMinY());
                out.writeDouble(bounds.getMaxY());
            }
        }
    }

    public static BucketSet decode(DataBuffer data, final int offset) {
        final int size = data.getInt(offset);
        if (0 == size) {
            return BucketSet.EMPTY;
        }
        return new BucketSet(size, data, offset);
    }

    private final int size;

    private final DataBuffer data;

    private final int offset;

    public BucketSet(final int size, final DataBuffer data, final int offset) {
        this.size = size;
        this.data = data;
        this.offset = offset;
    }

    public ImmutableSortedMap<Integer, Bucket> build() {
        int off = offset + HEADER_SIZE;
        Builder<Integer, Bucket> builder = ImmutableSortedMap.naturalOrder();
        DataInput in = data.asDataInput(off);
        try {
            int index;
            byte[] oidbuff = new byte[ObjectId.NUM_BYTES];
            ObjectId id;
            double minx, maxx, miny, maxy;
            Envelope bounds;
            for (int i = 0; i < size; i++) {
                index = in.readUnsignedByte();
                in.readFully(oidbuff);
                id = new ObjectId(oidbuff);
                minx = in.readDouble();
                if (Double.isNaN(minx)) {
                    bounds = null;
                } else {
                    maxx = in.readDouble();
                    miny = in.readDouble();
                    maxy = in.readDouble();
                    bounds = new Envelope(minx, maxx, miny, maxy);
                }
                Bucket bucket = Bucket.create(id, bounds);
                builder.put(Integer.valueOf(index), bucket);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return builder.build();
    }

}
