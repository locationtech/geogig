/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain.index;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.index.BuildFullHistoryIndexOp;
import org.locationtech.geogig.plumbing.index.BuildIndexOp;
import org.locationtech.geogig.plumbing.index.CreateIndexInfoOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;

/**
 * Creates a new index using the provided parameters and metadata.
 */
public class CreateIndexOp extends AbstractGeoGigOp<Index> {

    private String treeName;

    private String attributeName;

    private IndexType indexType;

    private @Nullable Map<String, Object> metadata;

    private RevTree canonicalTypeTree;

    private ObjectId featureTypeId;

    private boolean indexHistory = false;

    /**
     * Performs the operation.
     * 
     * @return an {@link Index} that represents the newly created index
     */
    @Override
    protected Index _call() {
        checkArgument(treeName != null, "treeName not provided");
        checkArgument(attributeName != null, "attributeName not provided");
        checkArgument(indexType != null, "indexType not provided");
        checkArgument(canonicalTypeTree != null, "canonicalTypeTree not provided");
        checkArgument(featureTypeId != null, "featureTypeId not provided");

        final IndexInfo indexInfo = command(CreateIndexInfoOp.class)//
                .setTreeName(treeName)//
                .setAttributeName(attributeName)//
                .setIndexType(indexType)//
                .setMetadata(metadata)//
                .call();

        ObjectId indexedTreeId = null;

        try {
            if (indexHistory) {
                command(BuildFullHistoryIndexOp.class)//
                        .setTreeRefSpec(treeName)//
                        .setAttributeName(attributeName)//
                        .setProgressListener(getProgressListener())//
                        .call();
                if (!getProgressListener().isCanceled()) {
                    Optional<ObjectId> headIndexedTreeId = indexDatabase()
                            .resolveIndexedTree(indexInfo, canonicalTypeTree.getId());
                    checkState(headIndexedTreeId.isPresent(),
                            "HEAD indexed tree could not be resolved after building history indexes.");
                    indexedTreeId = headIndexedTreeId.get();
                }
            } else {
                indexedTreeId = command(BuildIndexOp.class)//
                        .setIndex(indexInfo)//
                        .setOldCanonicalTree(RevTree.EMPTY)//
                        .setNewCanonicalTree(canonicalTypeTree)//
                        .setRevFeatureTypeId(featureTypeId)//
                        .setProgressListener(getProgressListener())//
                        .call().getId();
            }
        } catch (Exception e) {
            // rollback
            rollback();
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }

        if (getProgressListener().isCanceled()) {
            rollback();
            return null;
        }

        return new Index(indexInfo, indexedTreeId, indexDatabase());
    }

    private void rollback() {
        command(DropIndexOp.class).setTreeRefSpec(treeName).setAttributeName(attributeName).call();
    }

    /**
     * @param treeName the name of the feature tree
     * @return {@code this}
     */
    public CreateIndexOp setTreeName(String treeName) {
        this.treeName = treeName;
        return this;
    }

    /**
     * @param attributeName the name of the indexed attribute
     * @return {@code this}
     */
    public CreateIndexOp setAttributeName(String attributeName) {
        this.attributeName = attributeName;
        return this;
    }

    /**
     * @param indexType the type of index to create
     * @return {@code this}
     */
    public CreateIndexOp setIndexType(IndexType indexType) {
        this.indexType = indexType;
        return this;
    }

    /**
     * @param metadata extra data that can be used by the index
     * @return {@code this}
     */
    public CreateIndexOp setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }

    /**
     * @param canonicalTypeTree the canonical {@link RevTree} of the feature type
     * @return {@code this}
     */
    public CreateIndexOp setCanonicalTypeTree(RevTree canonicalTypeTree) {
        this.canonicalTypeTree = canonicalTypeTree;
        return this;
    }

    /**
     * @param featureTypeId the {@link ObjectId} of the feature type
     * @return {@code this}
     */
    public CreateIndexOp setFeatureTypeId(ObjectId featureTypeId) {
        this.featureTypeId = featureTypeId;
        return this;
    }

    /**
     * Build index trees for the full history of the feature tree.
     * 
     * @param indexHistory if {@code true}, index trees will be built for every commit that contains
     *        the given feature type tree
     * @return {@code this}
     */
    public CreateIndexOp setIndexHistory(boolean indexHistory) {
        this.indexHistory = indexHistory;
        return this;
    }
}
