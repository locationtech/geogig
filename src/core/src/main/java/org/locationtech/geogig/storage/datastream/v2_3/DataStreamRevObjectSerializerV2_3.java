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

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV2;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;

/**
 * Serialization factory for serial version 2.2
 */
@Beta
public class DataStreamRevObjectSerializerV2_3 extends DataStreamRevObjectSerializerV2 {

    public static final DataStreamRevObjectSerializerV2_3 INSTANCE = new DataStreamRevObjectSerializerV2_3();

    public DataStreamRevObjectSerializerV2_3() {
        super(FormatCommonV2_3.INSTANCE);
    }

    @Override
    public RevObject read(@Nullable ObjectId id, byte[] data, int offset, int length)
            throws IOException {
        final int type = data[offset] & 0xFF;
        if (RevObject.TYPE.TREE.ordinal() == type) {
            final int size = Ints.fromBytes(data[offset + 1], data[offset + 2], data[offset + 3],
                    data[offset + 4]);
            offset += 1 + Integer.BYTES;// skip size header
            length -= 1 + Integer.BYTES;
            Preconditions.checkState(size == length, "expected %s, got %s", size, length);
            return FormatCommonV2_3.INSTANCE.readTree(id, data, offset, length);
        }
        return super.read(id, new ByteArrayInputStream(data, offset, length));
    }

    @Override
    public String getDisplayName() {
        return "Binary 2.3";
    }
}
