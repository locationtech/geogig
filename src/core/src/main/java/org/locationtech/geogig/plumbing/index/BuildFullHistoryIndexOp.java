/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.plumbing.index;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.index.IndexUtils;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.ProgressListener;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Builds an index for every commit a given type tree is present at. Returns the number of trees
 * that were built.
 */
public class BuildFullHistoryIndexOp extends AbstractGeoGigOp<Integer> {

    private String treeRefSpec;

    private @Nullable String attributeName;

    private boolean onlyMissing;

    /**
     * @param treeRefSpec the tree refspec of the index to be built
     * @return {@code this}
     */
    public BuildFullHistoryIndexOp setTreeRefSpec(String treeRefSpec) {
        this.treeRefSpec = treeRefSpec;
        return this;
    }

    /**
     * @param attributeName the indexed attribute
     * @return {@code this}
     */
    public BuildFullHistoryIndexOp setAttributeName(String attributeName) {
        this.attributeName = attributeName;
        return this;
    }

    public BuildFullHistoryIndexOp setMissingOnly(boolean onlyMissing) {
        this.onlyMissing = onlyMissing;
        return this;
    }

    /**
     * Performs the operation.
     * 
     * @return the number of trees that were built
     */
    @Override
    protected Integer _call() {
        checkArgument(treeRefSpec != null, "Tree ref spec not provided.");

        final NodeRef typeTreeRef = IndexUtils.resolveTypeTreeRef(context(), treeRefSpec);
        checkArgument(typeTreeRef != null, "Can't find feature tree '%s'", treeRefSpec);
        String treeName = typeTreeRef.path();
        List<IndexInfo> indexInfos = IndexUtils.resolveIndexInfo(indexDatabase(), treeName,
                attributeName);
        checkState(!indexInfos.isEmpty(), "A matching index could not be found.");
        checkState(indexInfos.size() == 1,
                "Multiple indexes were found for the specified tree, please specify the attribute.");

        IndexInfo index = indexInfos.get(0);

        if (!onlyMissing) {
            indexDatabase().clearIndex(index);
        }
        int builtTrees = indexHistory(index);
        return builtTrees;
    }

    /**
     * Builds an index on every reachable commit in the history.
     * 
     * @param index the {@link IndexInfo} to use
     * @return the number of trees that were built
     */
    private int indexHistory(IndexInfo index) {
        ImmutableList<Ref> branches = command(BranchListOp.class).setLocal(true).setRemotes(true)
                .call();
        int builtTrees = 0;
        ProgressListener listener = getProgressListener();
        for (Ref ref : branches) {
            if (listener.isCanceled()) {
                break;
            }
            getProgressListener().setDescription("Building index for %s:%s at %s",
                    index.getTreeName(), index.getAttributeName(), ref.getName());
            Iterator<RevCommit> commits = command(LogOp.class).setUntil(ref.getObjectId()).call();
            while (commits.hasNext()) {
                if (listener.isCanceled()) {
                    break;
                }
                RevCommit next = commits.next();
                if (indexCommit(index, next)) {
                    builtTrees++;
                }
            }
        }
        return builtTrees;
    }

    /**
     * Builds an index for a single commit.
     * 
     * @param index the {@link IndexInfo} to use
     * @param commit the commit to build the index for
     * @return {@code true} if an index tree was built, {@code false} otherwise
     */
    private boolean indexCommit(IndexInfo index, RevCommit commit) {
        RevTree commitTree = objectDatabase().getTree(commit.getTreeId());
        Optional<NodeRef> treeNode = command(FindTreeChild.class).setChildPath(index.getTreeName())
                .setParent(commitTree).call();
        if (!treeNode.isPresent()) {
            return false;
        }
        if (indexDatabase().resolveIndexedTree(index, treeNode.get().getObjectId()).isPresent()) {
            return false;
        }
        RevTree newCanonicalTree = objectDatabase().getTree(treeNode.get().getObjectId());
        ImmutableList<ObjectId> oldCommits = graphDatabase().getChildren(commit.getId());
        RevTree oldCanonicalTree = RevTree.EMPTY;
        for (ObjectId oldCommitId : oldCommits) {
            Optional<ObjectId> oldTreeId = command(ResolveTreeish.class)
                    .setTreeish(oldCommitId.toString() + ":" + index.getTreeName()).call();
            if (oldTreeId.isPresent()
                    && indexDatabase().resolveIndexedTree(index, oldTreeId.get()).isPresent()) {
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
        if (getProgressListener().isCanceled()) {
            return false;
        }
        return true;
    }
}
