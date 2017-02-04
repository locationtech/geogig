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

import com.google.common.base.Throwables;

public class IndexInfoSerializer {

    public static void serialize(IndexInfo index, DataOutput out) {
        try {
            DataStreamValueSerializerV2.write(index.getTreeName(), out);
            DataStreamValueSerializerV2.write(index.getAttributeName(), out);
            DataStreamValueSerializerV2.write(index.getIndexType().toString(), out);
            DataStreamValueSerializerV2.write(index.getMetadata(), out);
        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static IndexInfo deserialize(DataInput in) {
        String treeName;
        String attributeName;
        IndexType indexType;
        Map<String, Object> metadata;
        try {
            treeName = (String) DataStreamValueSerializerV2.read(FieldType.STRING, in);
            attributeName = (String) DataStreamValueSerializerV2.read(FieldType.STRING, in);
            indexType = IndexType
                    .valueOf((String) DataStreamValueSerializerV2.read(FieldType.STRING, in));
            metadata = (Map<String, Object>) DataStreamValueSerializerV2.read(FieldType.MAP, in);
        } catch (IOException ioe) {
            throw Throwables.propagate(ioe);
        }
        return new IndexInfo(treeName, attributeName, indexType, metadata);
    }
}
