/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;

import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.storage.datastream.DataStreamValueSerializerV2;
import org.locationtech.geogig.storage.datastream.ValueSerializer;

public class IndexInfoSerializer {

    private static final ValueSerializer valueEncoder = DataStreamValueSerializerV2.INSTANCE;

    public static void serialize(IndexInfo index, DataOutput out) {
        try {
            valueEncoder.encode(FieldType.STRING, index.getTreeName(), out);
            valueEncoder.encode(FieldType.STRING, index.getAttributeName(), out);
            valueEncoder.encode(FieldType.STRING, index.getIndexType().toString(), out);
            valueEncoder.encode(FieldType.MAP, index.getMetadata(), out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static IndexInfo deserialize(DataInput in) {
        String treeName;
        String attributeName;
        IndexType indexType;
        Map<String, Object> metadata;
        try {
            treeName = valueEncoder.readString(in);
            attributeName = valueEncoder.readString(in);
            indexType = IndexType.valueOf(valueEncoder.readString(in));
            metadata = valueEncoder.readMap(in);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        return new IndexInfo(treeName, attributeName, indexType, metadata);
    }
}
