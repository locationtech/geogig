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
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.hash.Hasher;

import lombok.ToString;

public @ToString final class IndexInfo {
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
                    && compareMetadatas(getMetadata(), i.getMetadata());
        }
        return false;
    }

    /**
     * Properly compares two IndexInfo metadatas.  Since some of the values in the map
     * can be String[], normal Objects.equals() do not work.  We use a better comparison.
     *
     * @param meta1
     * @param meta2
     * @return
     */
    public static boolean compareMetadatas(Map<String, Object> meta1, Map<String, Object> meta2) {
        if ((meta1 == null) && (meta2 == null))
            return true; // both null
        if ((meta1 == null) || (meta2 == null))
            return false; // one null, the other not
        if (meta1 == meta2)
            return true; //both the same reference
        if (meta1.size() != meta2.size())
            return false; //different # of entries

        for (Map.Entry<String, Object> entry : meta1.entrySet()) {
            if (!meta2.containsKey(entry.getKey()))
                return false;
            Object v1 = entry.getValue();
            Object v2 = meta2.get(entry.getKey());
            if (!Objects.deepEquals(v1, v2))
                return false;
        }
        return true;
    }


    @Override
    public int hashCode() {
        return Objects.hash(getTreeName(), getAttributeName(), getIndexType(), getMetadata().size());
    }

    public static ObjectId getIndexId(String treeName, String attributeName) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        hasher.putBytes(treeName.getBytes(Charsets.UTF_8));
        hasher.putBytes(attributeName.getBytes(Charsets.UTF_8));
        return ObjectId.create(hasher.hash().asBytes());
    }

    public static Set<String> getMaterializedAttributeNames(IndexInfo info) {
        Set<String> availableAttNames = ImmutableSet.of();

        final @Nullable String[] attNames = (String[]) info.getMetadata()
                .get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
        if (attNames != null) {
            availableAttNames = Sets.newHashSet(attNames);
        }
        return availableAttNames;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getMaterializedAttributes(Node n) {
        Object v = n.getExtraData(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);
        Preconditions.checkArgument(v == null || v instanceof Map);
        return (Map<String, Object>) v;
    }

    public static @Nullable Object getMaterializedAttribute(String attName, Node n) {
        Map<String, Object> ma = getMaterializedAttributes(n);
        Object o = ma == null ? null : ma.get(attName);
        return o;
    }

    public static @Nullable Envelope getMaxBounds(IndexInfo info) {
        Envelope maxBounds = (Envelope) info.getMetadata().get(IndexInfo.MD_QUAD_MAX_BOUNDS);
        return maxBounds;
    }
}
