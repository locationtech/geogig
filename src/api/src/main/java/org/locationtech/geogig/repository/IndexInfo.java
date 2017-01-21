/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hasher;

public class IndexInfo {
    public static enum IndexType {
        QUADTREE
    };

    private final ObjectId indexId;

    private final String treeName;

    private final String attributeName;

    private final IndexType indexType;

    private final Map<String, Object> metadata;

    public IndexInfo(String treeName, String attributeName, IndexType indexType,
            @Nullable Map<String, Object> metadata) {
        this.indexId = getIndexId(treeName, attributeName);
        this.treeName = treeName;
        this.attributeName = attributeName;
        this.indexType = indexType;
        this.metadata = metadata == null ? ImmutableMap.of() : ImmutableMap.copyOf(metadata);
    }

    public ObjectId getId() {
        return indexId;
    }

    public String getTreeName() {
        return treeName;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public IndexType getIndexType() {
        return indexType;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public static ObjectId getIndexId(String treeName, String attributeName) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        hasher.putBytes(treeName.getBytes(Charsets.UTF_8));
        hasher.putBytes(attributeName.getBytes(Charsets.UTF_8));
        return ObjectId.createNoClone(hasher.hash().asBytes());
    }
}
