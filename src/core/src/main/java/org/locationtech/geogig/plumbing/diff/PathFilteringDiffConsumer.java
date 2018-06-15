/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import java.util.List;

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;

/**
 * A {@link Consumer} decorator that checks for whether each tree/bucket/feature event applies to
 * the provided list of filters before delegating to the actual {@code DiffTreeVisitor.Consumer},
 * which hence will only be notified of the events that apply to the given path filters.
 */
public class PathFilteringDiffConsumer extends PreOrderDiffWalk.ForwardingConsumer {

    private DiffPathFilter filter;

    public PathFilteringDiffConsumer(List<String> pathFilters, PreOrderDiffWalk.Consumer delegate) {
        super(delegate);
        this.filter = new DiffPathFilter(pathFilters);
    }

    @Override
    public boolean tree(NodeRef left, NodeRef right) {
        String path = left == null ? right.path() : left.path();
        if (filter.treeApplies(path)) {
            return super.tree(left, right);
        }
        return false;
    }

    @Override
    public void endTree(NodeRef left, NodeRef right) {
        String path = left == null ? right.path() : left.path();
        if (filter.treeApplies(path)) {
            super.endTree(left, right);
        }
    }

    @Override
    public boolean bucket(NodeRef lparent, NodeRef rparent, BucketIndex bucketIndex, Bucket left,
            Bucket right) {
        String treePath = lparent == null ? rparent.path() : lparent.path();

        if (filter.bucketApplies(treePath, bucketIndex)) {
            return super.bucket(lparent, rparent, bucketIndex, left, right);
        }
        return false;
    }

    @Override
    public boolean feature(NodeRef left, NodeRef right) {
        String parent = left == null ? right.getParentPath() : left.getParentPath();
        String node = left == null ? right.name() : left.name();
        if (filter.featureApplies(parent, node)) {
            return super.feature(left, right);
        }
        return true;
    }

}
