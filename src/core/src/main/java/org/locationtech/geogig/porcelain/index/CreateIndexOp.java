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

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.index.BuildIndexOp;
import org.locationtech.geogig.plumbing.index.CreateIndexInfoOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;

public class CreateIndexOp extends AbstractGeoGigOp<Index> {

    private String treeName;

    private String attributeName;

    private IndexType indexType;

    private @Nullable Map<String, Object> metadata;

    private RevTree canonicalTypeTree;

    private ObjectId featureTypeId;

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

        final RevTree indexTree = command(BuildIndexOp.class)//
                .setIndex(indexInfo)//
                .setOldCanonicalTree(RevTree.EMPTY)//
                .setNewCanonicalTree(canonicalTypeTree)//
                .setRevFeatureTypeId(featureTypeId)//
                .setProgressListener(getProgressListener())//
                .call();

        return new Index(indexInfo, indexTree.getId(), indexDatabase());
    }

    public CreateIndexOp setTreeName(String treeName) {
        this.treeName = treeName;
        return this;
    }

    public CreateIndexOp setAttributeName(String attributeName) {
        this.attributeName = attributeName;
        return this;
    }

    public CreateIndexOp setIndexType(IndexType indexType) {
        this.indexType = indexType;
        return this;
    }

    public CreateIndexOp setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }

    public CreateIndexOp setCanonicalTypeTree(RevTree canonicalTypeTree) {
        this.canonicalTypeTree = canonicalTypeTree;
        return this;
    }

    public CreateIndexOp setFeatureTypeId(ObjectId featureTypeId) {
        this.featureTypeId = featureTypeId;
        return this;
    }
}
