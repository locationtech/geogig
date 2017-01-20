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
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hasher;
import com.vividsolutions.jts.geom.Envelope;

public class IndexInfo {
    public static enum IndexType {
        QUADTREE
    }

    /**
     * Key by which the quad-tree max bounds is stored in the index {@link #getMetadata() metadata}
     * as an {@link Envelope} instance.
     */
    public static final String MD_QUAD_MAX_BOUNDS = "QUAD_MAX_BOUNDS";

    /**
     * Key by which the feature attribute values is stored on {@link Node#getExtraData()} for
     * materialized index {@link RevTree}s.
     */
    public static final String FEATURE_ATTRIBUTES_EXTRA_DATA = "@attributes";

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

    @Override
    public boolean equals(Object o) {
        if (o instanceof IndexInfo) {
            IndexInfo i = (IndexInfo) o;
            return Objects.equals(getTreeName(), i.getTreeName())
                    && Objects.equals(getAttributeName(), i.getAttributeName())
                    && Objects.equals(getIndexType(), i.getIndexType())
                    && Objects.equals(getMetadata(), i.getMetadata());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTreeName(), getAttributeName(), getIndexType(), getMetadata());
    }

    public static ObjectId getIndexId(String treeName, String attributeName) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        hasher.putBytes(treeName.getBytes(Charsets.UTF_8));
        hasher.putBytes(attributeName.getBytes(Charsets.UTF_8));
        return ObjectId.createNoClone(hasher.hash().asBytes());
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMaterializedAttributes(Node n) {
        Map<String, Object> extraData = n.getExtraData();
        if (extraData == null) {
            return ImmutableMap.of();
        }
        Object v = extraData.get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
        Preconditions.checkArgument(v == null || v instanceof Map);
        if (v == null) {
            return ImmutableMap.of();
        }
        return (Map<String, Object>) v;
    }
}
