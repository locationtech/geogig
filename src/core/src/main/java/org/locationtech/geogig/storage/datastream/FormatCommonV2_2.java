/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream;

import static org.locationtech.geogig.storage.datastream.Varint.readSignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeSignedVarInt;
import static org.locationtech.geogig.storage.datastream.Varint.writeUnsignedVarInt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Preconditions;

public class FormatCommonV2_2 extends FormatCommonV2_1 {

    public static final FormatCommonV2_2 INSTANCE = new FormatCommonV2_2();

    @Override
    protected void writeBucket(final Bucket bucket, DataOutput data, Envelope envBuff)
            throws IOException {

        writeUnsignedVarInt(bucket.getIndex(), data);

        bucket.getObjectId().writeTo(data);
        envBuff.setToNull();
        bucket.expand(envBuff);
        writeBounds(envBuff, data);
    }

    @Override
    protected final Bucket readBucketBody(int bucketIndex, DataInput in) throws IOException {
        ObjectId objectId = readObjectId(in);
        @Nullable
        final Envelope bounds = readBounds(in);
        return RevObjectFactory.defaultInstance().createBucket(objectId, bucketIndex, bounds);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Node readNode(DataInput in) throws IOException {
        final int typeAndMasks = in.readByte() & 0xFF;
        final int nodeType = typeAndMasks & TYPE_READ_MASK;
        // final int boundsMask = typeAndMasks & BOUNDS_READ_MASK; //unused
        final int metadataMask = typeAndMasks & METADATA_READ_MASK;
        final int extraDataMask = typeAndMasks & EXTRA_DATA_READ_MASK;

        final RevObject.TYPE contentType = RevObject.TYPE.valueOf(nodeType);
        final String name = in.readUTF();
        final ObjectId objectId = readObjectId(in);
        ObjectId metadataId = ObjectId.NULL;
        if (metadataMask == METADATA_PRESENT_MASK) {
            metadataId = readObjectId(in);
        }
        @Nullable
        final Envelope bbox = readBounds(in);

        Map<String, Object> extraData = null;
        if (extraDataMask == EXTRA_DATA_PRESENT_MASK) {
            Object extra = valueEncoder.decode(FieldType.MAP, in);
            Preconditions.checkState(extra instanceof Map);
            extraData = (Map<String, Object>) extra;
        }

        final Node node;
        node = RevObjectFactory.defaultInstance().createNode(name, objectId, metadataId,
                contentType, bbox, extraData);
        return node;
    }

    @Override
    public void writeNode(Node node, DataOutput data, Envelope env) throws IOException {
        // Encode the node type and the bounds and metadata presence masks in one single byte:
        // - bits 1-3 for the object type (up to 8 types, there are only 5 and no plans to add more)
        // - bits 4-5 bits for the bounds mask
        // - bit 6 metadata id present(1) or absent(0)
        // - bit 7 extra data present(1) or absent(0)
        // - bit 8 unused

        final int nodeType = node.getType().value();
        final int boundsMask;
        final int metadataMask;
        final int extraDataMask;

        env.setToNull();
        node.expand(env);

        boundsMask = BOUNDS_NULL_MASK; // we don't need this anymore, set to 0

        final Map<String, Object> extraData = node.getExtraData();

        metadataMask = node.getMetadataId().isPresent() ? METADATA_PRESENT_MASK
                : METADATA_ABSENT_MASK;

        extraDataMask = extraData.isEmpty() ? EXTRA_DATA_ABSENT_MASK : EXTRA_DATA_PRESENT_MASK;

        // encode type and bounds mask together
        final int typeAndMasks = nodeType | boundsMask | metadataMask | extraDataMask;

        data.writeByte(typeAndMasks);
        data.writeUTF(node.getName());
        node.getObjectId().writeTo(data);
        if (metadataMask == METADATA_PRESENT_MASK) {
            node.getMetadataId().or(ObjectId.NULL).writeTo(data);
        }
        writeBounds(env, data);

        if (extraDataMask == EXTRA_DATA_PRESENT_MASK) {
            valueEncoder.encode(extraData, data);
        }
    }

    /**
     * This assumes that envelope is on the float32 grid (should be the case with Bounded objects)
     * 
     * @param envelope
     * @param data
     * @throws IOException
     */
    private static void writeBounds(Envelope envelope, DataOutput data) throws IOException {
        // directly use the default encoding
        int[] serializedForm = Float32BoundsSerializer.serialize(envelope);
        writeSignedVarInt(serializedForm[0], data);
        writeSignedVarInt(serializedForm[1], data);
        writeSignedVarInt(serializedForm[2], data);
        writeSignedVarInt(serializedForm[3], data);
    }

    private static Envelope readBounds(DataInput in) throws IOException {
        // directly use the default encoding
        int[] serializedForm = new int[] { readSignedVarInt(in), readSignedVarInt(in),
                readSignedVarInt(in), readSignedVarInt(in) };
        return Float32BoundsSerializer.deserialize(serializedForm);
    }

}
