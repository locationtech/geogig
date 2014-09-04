/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.diff;

import java.util.List;

import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.plumbing.diff.DiffTreeVisitor.Consumer;

/**
 * A {@link Consumer} decorator that checks for whether each tree/bucket/feature event applies to
 * the provided list of filters before delegating to the actual {@code DiffTreeVisitor.Consumer},
 * which hence will only be notified of the events that apply to the given path filters.
 */
public class PathFilteringDiffConsumer extends DiffTreeVisitor.ForwardingConsumer {

    private DiffPathTracker tracker;

    private DiffPathFilter filter;

    public PathFilteringDiffConsumer(List<String> pathFilters, DiffTreeVisitor.Consumer delegate) {
        super(delegate);
        this.tracker = new DiffPathTracker();
        this.filter = new DiffPathFilter(pathFilters);
    }

    @Override
    public boolean tree(Node left, Node right) {
        String path = tracker.tree(left, right);
        if (filter.treeApplies(path)) {
            return super.tree(left, right);
        }
        return false;
    }

    @Override
    public void endTree(Node left, Node right) {
        String currentPath = tracker.getCurrentPath();
        tracker.endTree(left, right);
        if (filter.treeApplies(currentPath)) {
            super.endTree(left, right);
        }
    }

    @Override
    public boolean bucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
        String treePath = tracker.getCurrentPath();
        if (filter.bucketApplies(treePath, bucketIndex, bucketDepth)) {
            return super.bucket(bucketIndex, bucketDepth, left, right);
        }
        return false;
    }

    @Override
    public void feature(Node left, Node right) {
        String treePath = tracker.getCurrentPath();
        String featureName = tracker.name(left, right);
        String featurePath = NodeRef.appendChild(treePath, featureName);

        if (filter.featureApplies(featurePath)) {
            super.feature(left, right);
        }
    }

}
