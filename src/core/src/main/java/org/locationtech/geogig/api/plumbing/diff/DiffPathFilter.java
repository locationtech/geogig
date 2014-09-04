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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.storage.NodePathStorageOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

/**
 * A helper class for {@link PathFilteringDiffConsumer} that evaluates whether path filters apply
 * whenever the consumer gets an tree/bucket/feature event.
 * 
 * @see PathFilteringDiffConsumer
 */
final class DiffPathFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiffPathFilter.class);

    private static final NodePathStorageOrder ORDER = new NodePathStorageOrder();

    private List<String> pathFilters;

    public DiffPathFilter(List<String> filters) {
        Preconditions.checkNotNull(filters, "filter list is null");
        Preconditions.checkArgument(!filters.isEmpty(), "Don't use an empty filter list");
        this.pathFilters = new ArrayList<String>(new HashSet<String>(filters));
        for (String s : this.pathFilters) {
            if (Strings.isNullOrEmpty(s)) {
                throw new IllegalArgumentException(String.format(
                        "Empty or null filters not allowed: %s",
                        Arrays.toString(this.pathFilters.toArray())));
            }
        }
    }

    public String name(Node left, Node right) {
        return left == null ? right.getName() : left.getName();
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
        String filter;
        boolean applies = false;
        for (int i = 0; i < pathFilters.size(); i++) {
            filter = pathFilters.get(i);
            if (filter.equals(treePath)) {
                applies = true;
            } else {
                boolean filterIsChildOfTree = NodeRef.isChild(treePath, filter);
                if (filterIsChildOfTree) {
                    applies = true;
                }
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Filter: '{}', tree: '{}', applies: {}", filter, treePath, applies);
            }
            if (applies) {
                return true;
            }
        }
        return false;
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
     * @param bucketDepth
     * @return
     */
    public boolean bucketApplies(final String treePath, final int bucketIndex, final int bucketDepth) {
        String filter;
        for (int i = 0; i < pathFilters.size(); i++) {
            filter = pathFilters.get(i);
            if (filter.equals(treePath)) {
                // all buckets apply
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Filter: '{}', tree: '{}', depth: {}, bucket idx {}, applies: {}",
                            filter, treePath, bucketDepth, bucketIndex, true);
                }
                return true;
            }
            boolean filterIsChildOfTree = NodeRef.isChild(treePath, filter);
            if (filterIsChildOfTree) {
                ImmutableList<String> filterSteps = NodeRef.split(filter);
                ImmutableList<String> treeSteps = NodeRef.split(treePath);

                String childName = filterSteps.get(treeSteps.size());
                int childBucket = ORDER.bucket(childName, bucketDepth);
                boolean applies = childBucket == bucketIndex;

                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace(
                            "Filter: '{}', tree: '{}', depth: {}, bucket idx {}, child bucket: {}, child name: '{}', applies: {}",
                            filter, treePath, bucketDepth, bucketIndex, childBucket, childName,
                            applies);
                }
                if (applies) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @param featurePath path o a feature node (e.g. {@code 'roads/road1'}
     * @return {@code true} if {@code featurePath} is a child of, or equals to, one of the path
     *         filters.
     */
    public boolean featureApplies(final String featurePath) {
        String filter;
        boolean applies = false;
        for (int i = 0; i < pathFilters.size(); i++) {
            filter = pathFilters.get(i);
            if (filter.equals(featurePath)) {
                applies = true;
            } else if (NodeRef.isChild(filter, featurePath)) {
                applies = true;
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Filter: '{}', feature: '{}', applies: {}", filter, featurePath,
                        applies);
            }
            if (applies) {
                return true;
            }
        }
        return false;
    }
}
