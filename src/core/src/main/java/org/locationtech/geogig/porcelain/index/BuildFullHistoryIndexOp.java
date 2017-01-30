/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.porcelain.index;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.Iterator;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.index.BuildIndexOp;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.NodeRef;
import org.opengis.feature.type.GeometryDescriptor;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Creates a spatial index for every commit a given type tree is present at.
 *
 */
public class BuildFullHistoryIndexOp extends AbstractGeoGigOp<Integer> {

    private String treeRefSpec;

    private @Nullable String attributeName;

    public BuildFullHistoryIndexOp setTreeRefSpec(String treeRefSpec) {
        this.treeRefSpec = treeRefSpec;
        return this;
    }

    public BuildFullHistoryIndexOp setAttributeName(String attributeName) {
        this.attributeName = attributeName;
        return this;
    }

    @Override
    protected Integer _call() {
        checkArgument(treeRefSpec != null, "treeRefSpec not provided");

        final NodeRef typeTreeRef = IndexUtils.resolveTypeTreeRef(context(), treeRefSpec);
        RevFeatureType featureType = objectDatabase().getFeatureType(typeTreeRef.getMetadataId());
        String treeName = typeTreeRef.path();
        final GeometryDescriptor geometryAtt = IndexUtils.resolveGeometryAttribute(featureType,
                attributeName);
        final String geometryAttributeName = geometryAtt.getLocalName();

        Optional<IndexInfo> index = indexDatabase().getIndexInfo(treeName, geometryAttributeName);
        checkState(index.isPresent(), "a matching index could not be found");
        int builtTrees = 0;
        if (index.isPresent()) {
            builtTrees = indexHistory(index.get());
        }
        return builtTrees;
    }


    private int indexHistory(IndexInfo index) {
        ImmutableList<Ref> branches = command(BranchListOp.class).setLocal(true).setRemotes(true)
                .call();
        int builtTrees = 0;
        for (Ref ref : branches) {
            Iterator<RevCommit> commits = command(LogOp.class).setUntil(ref.getObjectId()).call();
            while (commits.hasNext()) {
                RevCommit next = commits.next();
                if (indexCommit(index, next)) {
                    builtTrees++;
                }
            }
        }
        return builtTrees;
    }

    private boolean indexCommit(IndexInfo index, RevCommit commit) {
        RevTree commitTree = objectDatabase().getTree(commit.getTreeId());
        Optional<NodeRef> treeNode = command(FindTreeChild.class).setChildPath(index.getTreeName())
                .setParent(commitTree).call();
        if (!treeNode.isPresent()) {
            return false;
        }
        RevTree newCanonicalTree = objectDatabase().getTree(treeNode.get().getObjectId());
        ImmutableList<ObjectId> oldCommits = graphDatabase().getChildren(commit.getId());
        RevTree oldCanonicalTree = RevTree.EMPTY;
        for (ObjectId oldCommitId : oldCommits) {
            Optional<ObjectId> oldTreeId = command(ResolveTreeish.class)
                    .setTreeish(oldCommitId.toString() + ":" + index.getTreeName()).call();
            if (oldTreeId.isPresent()) {
                oldCanonicalTree = objectDatabase().getTree(oldTreeId.get());
                break;
            }
        }
        command(BuildIndexOp.class)//
                .setIndex(index)//
                .setRevFeatureTypeId(treeNode.get().getMetadataId())//
                .setOldCanonicalTree(oldCanonicalTree)//
                .setNewCanonicalTree(newCanonicalTree)//
                .setProgressListener(getProgressListener())//
                .call();
        return true;
    }
}
