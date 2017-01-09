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

import org.locationtech.geogig.model.ObjectId;

import com.google.common.hash.Hasher;

public class Index {
    public static enum IndexType {
        QUADTREE
    };

    private final String treeName;

    private final String attributeName;

    private final IndexType indexType;

    private final ObjectId indexId;

    public Index(String treeName, String attributeName, IndexType indexType) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        hasher.putBytes(treeName.getBytes());
        hasher.putBytes(attributeName.getBytes());
        hasher.putInt(indexType.ordinal());
        this.treeName = treeName;
        this.attributeName = attributeName;
        this.indexType = indexType;
        this.indexId = ObjectId.createNoClone(hasher.hash().asBytes());
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

    public ObjectId getId() {
        return indexId;
    }
}
