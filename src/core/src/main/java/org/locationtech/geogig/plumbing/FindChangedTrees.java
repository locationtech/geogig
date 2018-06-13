package org.locationtech.geogig.plumbing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.collect.Lists;

/**
 * Fast way to compute which child trees have changed between two versions of a tree with tree nodes
 * (e.g. two root trees with any number of feature trees/layers)
 *
 */
public class FindChangedTrees extends AbstractGeoGigOp<List<DiffEntry>> {

    private String oldRefSpec, newRefSpec;

    private ObjectId oldTreeId, newTreeId;

    private RevTree oldTree, newTree;

    private final List<String> pathFilters = Lists.newLinkedList();

    private ObjectStore leftSource;

    private ObjectStore rightSource;

    protected @Override List<DiffEntry> _call() {
        TreeChangesCollector collector = new TreeChangesCollector();

        DiffTree diffTree = command(DiffTree.class)//
                .setPreserveIterationOrder(false)//
                .setReportFeatures(false)//
                .setReportTrees(true)//
                .setPathFilter(this.pathFilters)//
                .setLeftSource(leftSource)//
                .setRightSource(rightSource);

        if (oldTree != null) {
            diffTree.setOldTree(oldTree);
        } else if (oldTreeId != null) {
            diffTree.setOldTree(oldTreeId);
        } else {
            diffTree.setOldVersion(oldRefSpec);
        }

        if (newTree != null) {
            diffTree.setNewTree(newTree);
        } else if (newTreeId != null) {
            diffTree.setNewTree(newTreeId);
        } else {
            diffTree.setNewVersion(newRefSpec);
        }

        diffTree.call(collector);
        List<DiffEntry> res = new ArrayList<>(collector.queue);
        return res;
    }

    private static class TreeChangesCollector extends PreOrderDiffWalk.AbstractConsumer {

        final LinkedBlockingQueue<DiffEntry> queue = new LinkedBlockingQueue<>();

        public @Override boolean tree(@Nullable NodeRef left, @Nullable NodeRef right) {
            if (null == (left == null ? right : left).getParentPath()) {
                return true;
            }
            queue.add(new DiffEntry(left, right));
            return true;
        }
    };

    public FindChangedTrees setPathFilter(@Nullable List<String> pathFitlers) {
        this.pathFilters.clear();
        if (pathFitlers != null) {
            this.pathFilters.addAll(pathFitlers);
        }
        return this;
    }

    public FindChangedTrees setLeftSource(ObjectStore leftSource) {
        this.leftSource = leftSource;
        return this;
    }

    public FindChangedTrees setRightSource(ObjectStore rightSource) {
        this.rightSource = rightSource;
        return this;
    }

    /**
     * @param oldRefSpec the ref that points to the "old" version
     * @return {@code this}
     */
    public FindChangedTrees setOldTreeIsh(String oldRefSpec) {
        this.oldRefSpec = oldRefSpec;
        return this;
    }

    /**
     * @param newRefSpec the ref that points to the "new" version
     * @return {@code this}
     */
    public FindChangedTrees setNewTreeIsh(String newRefSpec) {
        this.newRefSpec = newRefSpec;
        return this;
    }

    /**
     * @param oldTreeId the {@link ObjectId} of the "old" tree
     * @return {@code this}
     */
    public FindChangedTrees setOldTreeIsh(ObjectId oldTreeId) {
        this.oldTreeId = oldTreeId;
        return this;
    }

    /**
     * @param newTreeId the {@link ObjectId} of the "new" tree
     * @return {@code this}
     */
    public FindChangedTrees setNewTreeIsh(ObjectId newTreeId) {
        this.newTreeId = newTreeId;
        return this;
    }

    /**
     * @param oldTreeId the {@link ObjectId} of the "old" tree
     * @return {@code this}
     */
    public FindChangedTrees setOldTreeIsh(RevTree oldTree) {
        this.oldTree = oldTree;
        return this;
    }

    /**
     * @param newTreeId the {@link ObjectId} of the "new" tree
     * @return {@code this}
     */
    public FindChangedTrees setNewTreeIsh(RevTree newTree) {
        this.newTree = newTree;
        return this;
    }
}
