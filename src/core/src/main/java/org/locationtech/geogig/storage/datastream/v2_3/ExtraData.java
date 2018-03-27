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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.DataInput;
import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.storage.datastream.ValueSerializer;
import org.locationtech.geogig.storage.datastream.v2_3.NodeSet.NodesetHeader;

class ExtraData {

    public static void encode(Map<String, Object> extraData, InternalDataOutput inline,
            StringTable stringTable) throws IOException {
        checkNotNull(extraData);

        ValueSerializer writer = DataStreamValueSerializerV2_3.create(() -> stringTable);
        writer.writeMap(extraData, inline);
    }

    public static @Nullable Object get(final NodeSet nodeset, final int nodeExtraDataRelativeOffset,
            final String key) throws IOException {

        final DataBuffer dataBuffer = nodeset.data;
        final Supplier<StringTable> stringTable = dataBuffer.getStringTable();
        if (-1 == stringTable.get().get(key)) {
            return null;
        }
        final DataInput nodeExtraData;
        {
            final int inlineExtraDataOffset = nodeset.header.extraDataOffset();
            final int nodeInlineExtraDataAbsoluteOffset = inlineExtraDataOffset
                    + nodeExtraDataRelativeOffset;

            nodeExtraData = dataBuffer.asDataInput(nodeInlineExtraDataAbsoluteOffset);
        }

        DataStreamValueSerializerV2_3 reader = DataStreamValueSerializerV2_3.create(stringTable);

        Object value = reader.findInMap(nodeExtraData, key);
        return value;
    }

    /**
     * @param nodeset
     * @param nodeExtraDataRelativeOffset offset of the inline extra data for the node, relative to
     *        {@link NodesetHeader#extraDataOffset()}
     * @return
     * @throws IOException
     */
    @Nullable
    public static Map<String, Object> decode(final NodeSet nodeset,
            final int nodeExtraDataRelativeOffset) throws IOException {

        final Supplier<StringTable> stringTable;
        final DataInput nodeExtraData;
        {
            final DataBuffer dataBuffer = nodeset.data;
            stringTable = dataBuffer.getStringTable();
            final int inlineExtraDataOffset = nodeset.header.extraDataOffset();

            final int nodeInlineExtraDataAbsoluteOffset = inlineExtraDataOffset
                    + nodeExtraDataRelativeOffset;

            nodeExtraData = dataBuffer.asDataInput(nodeInlineExtraDataAbsoluteOffset);
        }

        ValueSerializer reader = DataStreamValueSerializerV2_3.create(stringTable);
        Map<String, Object> extraData = reader.readMap(nodeExtraData);
        return extraData;
    }
}
