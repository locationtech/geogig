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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * A helper class for {@link PathFilteringDiffConsumer} that evaluates whether path filters apply
 * whenever the consumer gets an tree/bucket/feature event.
 * 
 * @see PathFilteringDiffConsumer
 */
final class DiffPathFilter {

    private PathFilter pathFilter;

    public DiffPathFilter(List<String> filters) {
        Preconditions.checkNotNull(filters, "filter list is null");
        Preconditions.checkArgument(!filters.isEmpty(), "Don't use an empty filter list");
        pathFilter = new PathFilter(NodeRef.ROOT);
        for (String f : filters) {
            Preconditions.checkArgument(!Strings.isNullOrEmpty(f));
            pathFilter.add(NodeRef.split(f));
        }
    }

    private static final class PathFilter {
        final String name;

        Map<String, PathFilter> children = null;

        PathFilter(String name) {
            this.name = name;
        }

        public void add(List<String> path) {
            Preconditions.checkArgument(!path.isEmpty());
            if (children == null) {
                children = new HashMap<>();
            }
            String child = path.get(0);
            PathFilter childFilter = children.computeIfAbsent(child, c -> new PathFilter(c));
            if (path.size() > 1) {
                childFilter.add(path.subList(1, path.size()));
            }
        }

        public boolean applies(final List<String> path) {
            final String root = path.get(0);
            if (name.equals(root)) {
                if (children == null) {
                    return true;// all children apply
                }
                if (path.size() == 1) {
                    return true;// reached the end of the argument path
                }
                List<String> childrenPath = path.subList(1, path.size());
                final String child = childrenPath.get(0);
                PathFilter childFilter = children.get(child);
                if (childFilter == null) {
                    return false;
                }
                return childFilter.applies(childrenPath);
            }
            return false;
        }

        public PathFilter find(List<String> childPath) {
            Preconditions.checkState(children != null);
            Preconditions.checkArgument(!childPath.isEmpty());

            final String root = childPath.get(0);
            Preconditions.checkArgument(name.equals(root));
            if (1 == childPath.size()) {
                return this;
            }
            PathFilter childFilter = children.get(childPath.get(1));
            Preconditions.checkArgument(childFilter != null);
            return childFilter.find(childPath.subList(1, childPath.size()));
        }

        public boolean bucketApplies(BucketIndex bucketIndex) {
            final int[] myIndices = getBucketIndices();
            final int[] bucketIndices = bucketIndex.getIndexPath();
            for (int i = 0; i < bucketIndices.length; i++) {
                int ours = myIndices[i];
                int theirs = bucketIndices[i];
                if (ours != theirs) {
                    return false;
                }
            }
            return true;
        }

        private int[] bucketIndices;

        private int[] getBucketIndices() {
            if (bucketIndices == null) {
                bucketIndices = CanonicalNodeNameOrder.allBuckets(this.name);
            }
            return bucketIndices;
        }
    }

    public List<String> toPath(String path, @Nullable String node) {
        ArrayList<String> pathlist = new ArrayList<>();
        pathlist.add(NodeRef.ROOT);
        pathlist.addAll(NodeRef.split(path));
        if (node != null) {
            pathlist.add(node);
        }
        return pathlist;
    }

    /**
     * Tests whether the given tree path applies to one of the path filters.
     * <p>
     * Given a path filer {@code roads/highway}, the following {@code treePaths}:
     * <ul>
     * <li>{@code roads]} applies
     * <li>{@code roads/highway]} applies
     * <li>{@code roads/secondary} does not apply
     * <li>{@code roads/highway/principal} applies
     * <li>{@code buildings[/**]} does not apply
     * </ul>
     * 
     * @param treePath
     * @return {@code true} if {@code treePath} is a parent of, or equals to, one of the path
     *         filters
     */
    public boolean treeApplies(final String treePath) {
        boolean applies = pathFilter.applies(toPath(treePath, null));
        return applies;
    }

    /**
     * If this method is called then {@link #treeApplies(String)} returned {@code true} for the pair
     * of trees the buckets belong to, meaning that {@code treePath} either matches exactly one of
     * the filters, or is a filter children. If the former, all tree buckets apply. If the later,
     * only the ones whose simple name
     * <ul>
     * <li>a filter refers to exactly the same tree than {@code treePath}, in which case all buckets
     * apply
     * <li>a filter is a child of {@code treePath}, in which case the bucket applies
     * </ul>
     * 
     * @param treePath the path of the tree the bucket belong to
     * @param bucketIndex
     * @return
     */
    public boolean bucketApplies(final String treePath, final BucketIndex bucketIndex) {
        // if we got here, then tree applies
        final PathFilter treeFilter = this.pathFilter.find(toPath(treePath, null));
        if (treeFilter.children == null) {
            return true;
        }

        Map<String, PathFilter> childFilters = treeFilter.children;
        for (PathFilter childFilter : childFilters.values()) {
            if (childFilter.bucketApplies(bucketIndex)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param featurePath path o a feature node (e.g. {@code 'roads/road1'}
     * @return {@code true} if {@code featurePath} is a child of, or equals to, one of the path
     *         filters.
     */
    public boolean featureApplies(final String parent, final String node) {
        List<String> path = toPath(parent, node);
        boolean applies = pathFilter.applies(path);
        return applies;
    }
}
