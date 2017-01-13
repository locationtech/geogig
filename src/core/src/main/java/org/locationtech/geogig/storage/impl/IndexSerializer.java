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
import org.locationtech.geogig.repository.Index;
import org.locationtech.geogig.repository.Index.IndexType;
import org.locationtech.geogig.storage.datastream.DataStreamValueSerializerV2;

public class IndexSerializer {
    public static void serialize(Index index, DataOutput out) throws IOException {
        DataStreamValueSerializerV2.write(index.getTreeName(), out);
        DataStreamValueSerializerV2.write(index.getAttributeName(), out);
        DataStreamValueSerializerV2.write(index.getIndexType().toString(), out);
        DataStreamValueSerializerV2.write(index.getMetadata(), out);
    }

    @SuppressWarnings("unchecked")
    public static Index deserialize(DataInput in) throws IOException {
        String treeName = (String) DataStreamValueSerializerV2.read(FieldType.STRING, in);
        String attributeName = (String) DataStreamValueSerializerV2.read(FieldType.STRING, in);
        IndexType indexType = IndexType
                .valueOf((String) DataStreamValueSerializerV2.read(FieldType.STRING, in));
        Map<String, Object> metadata = (Map<String, Object>) DataStreamValueSerializerV2
                .read(FieldType.MAP, in);
        return new Index(treeName, attributeName, indexType, metadata);
    }
}
