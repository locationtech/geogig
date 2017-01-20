/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.index;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.storage.IndexDatabase;

/**
 * Creates an {@link IndexInfo} and builds the index tree for it.
 * 
 * @see BuildIndexOp
 */
public class CreateIndexInfoOp extends AbstractGeoGigOp<IndexInfo> {

    private String treeName;

    private String attributeName;

    private IndexType indexType;

    private @Nullable Map<String, Object> metadata;

    public CreateIndexInfoOp setTreeName(String treeName) {
        this.treeName = treeName;
        return this;
    }

    public CreateIndexInfoOp setAttributeName(String attributeName) {
        this.attributeName = attributeName;
        return this;
    }

    public CreateIndexInfoOp setIndexType(IndexType indexType) {
        this.indexType = indexType;
        return this;
    }

    public CreateIndexInfoOp setMetadata(@Nullable Map<String, Object> metadata) {
        checkNotNull(metadata);
        this.metadata = new HashMap<>(metadata);
        return this;
    };

    @Override
    protected IndexInfo _call() {
        IndexDatabase indexDatabase = indexDatabase();
        checkArgument(treeName != null, "tree name not provided");
        checkArgument(attributeName != null, "indexing attribute name not provided");
        checkArgument(indexType != null, "index type not provided");

        checkState(!indexDatabase.getIndex(treeName, attributeName).isPresent(),
                "An index has already been created on that tree and attribute.");

        Map<String, Object> metadata = this.metadata;

        IndexInfo index = indexDatabase.createIndex(treeName, attributeName, indexType, metadata);

        return index;
    }
}
