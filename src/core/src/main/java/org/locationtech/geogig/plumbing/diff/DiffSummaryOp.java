/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.difference;
import static com.google.common.collect.Maps.uniqueIndex;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.google.common.base.Function;
import org.eclipse.jdt.annotation.Nullable;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.geogig.data.FindFeatureTypeTrees;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.DiffBounds;
import org.locationtech.geogig.plumbing.DiffCount;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.diff.DiffSummaryOp.LayerDiffSummary;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;
import org.opengis.geometry.BoundingBox;

import com.google.common.base.Optional;
import com.google.common.collect.MapDifference;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

public @Builder class DiffSummaryOp extends AbstractGeoGigOp<List<LayerDiffSummary>> {

    public static @Value @Builder class LayerDiffSummary {
        private String path;

        private ObjectId leftTreeish;

        private ObjectId rightTreeish;

        private Envelope leftBounds;

        private Envelope rightBounds;

        private long featuresAdded;

        private long featuresChanged;

        private long featuresRemoved;
    }

    private ObjectStore leftSource, rightSource;

    private String leftTreeish, rightTreeish;

    private RevTree leftTree, rightTree;

    protected @Override List<LayerDiffSummary> _call() {
        this.leftSource = resolveSource(this.leftSource);
        this.rightSource = resolveSource(this.rightSource);

        final RevObject left = resolve(leftTreeish, leftTree, leftSource);
        final RevObject right = resolve(rightTreeish, rightTree, rightSource);

        final RevTree leftTree = resolveTree(left, leftSource);
        final RevTree rightTree = resolveTree(right, rightSource);

        List<LayerDiffSummary> result;

        Map<String, NodeRef[]> changedPaths = resolveChangedPaths(leftTree, rightTree);

        CompletableFuture<LayerDiffSummary> futures[] = run(leftTree, rightTree, changedPaths);
        CompletableFuture.allOf(futures).join();

        result = newArrayList(futures).stream().map((f) -> f.join()).collect(Collectors.toList());

        return result;
    }

    private ObjectStore resolveSource(ObjectStore source) {
        return source == null ? this.objectDatabase() : source;
    }

    private CompletableFuture<LayerDiffSummary>[] run(final RevTree left, final RevTree right,
            final Map<String, NodeRef[]> changedPaths) {

        List<CompletableFuture<LayerDiffSummary>> futures = new ArrayList<>(changedPaths.size());

        changedPaths.forEach((path, pair) -> futures.add(run(path, pair[0], pair[1])));

        @SuppressWarnings("unchecked")
        CompletableFuture<LayerDiffSummary>[] array = (CompletableFuture<LayerDiffSummary>[]) Array
                .newInstance(CompletableFuture.class, futures.size());
        return futures.toArray(array);
    }

    private CompletableFuture<LayerDiffSummary> run(final String path, final NodeRef left,
            final NodeRef right) {

        final ObjectId leftTreeId = left == null ? RevTree.EMPTY_TREE_ID : left.getObjectId();
        final ObjectId rightTreeId = right == null ? RevTree.EMPTY_TREE_ID : right.getObjectId();
        final RevTree leftTree = left == null ? RevTree.EMPTY : leftSource.getTree(leftTreeId);
        final RevTree rightTree = right == null ? RevTree.EMPTY : rightSource.getTree(rightTreeId);

        CompletableFuture<DiffObjectCount> diffCount = CompletableFuture.supplyAsync(() -> {
            DiffObjectCount count = command(DiffCount.class).setOldTree(leftTree)
                    .setNewTree(rightTree).setLeftSource(leftSource).setRightSource(rightSource)
                    .call();
            return count;
        });

        CompletableFuture<org.locationtech.geogig.plumbing.diff.DiffSummary<BoundingBox, BoundingBox>> diffBounds;
        diffBounds = CompletableFuture.supplyAsync(() -> {
            DiffSummary<BoundingBox, BoundingBox> boundsDiff;
            boundsDiff = command(DiffBounds.class).setOldVersion(leftTree).setNewVersion(rightTree)
                    .setLeftSource(leftSource).setRightSource(rightSource).call();
            return boundsDiff;
        });

        return diffCount.thenCombine(diffBounds,
                (count, bounds) -> toSummary(path, leftTree, rightTree, count, bounds));
    }

    private LayerDiffSummary toSummary(@NonNull String path, @Nullable RevTree left,
            @Nullable RevTree right, @NonNull DiffObjectCount count,
            @NonNull DiffSummary<BoundingBox, BoundingBox> bounds) {

        BoundingBox lb = bounds.getLeft();
        BoundingBox rb = bounds.getRight();
        ReferencedEnvelope leftBounds = lb.isEmpty() ? null : new ReferencedEnvelope(lb);
        ReferencedEnvelope rightBounds = rb.isEmpty() ? null : new ReferencedEnvelope(rb);

        LayerDiffSummary s = LayerDiffSummary.builder()//
                .path(path)//
                .leftTreeish(left.getId())//
                .rightTreeish(right.getId())//
                .leftBounds(leftBounds)//
                .rightBounds(rightBounds)//
                .featuresAdded(count.getFeaturesAdded())//
                .featuresChanged(count.getFeaturesChanged())//
                .featuresRemoved(count.getFeaturesRemoved())//
                .build();
        return s;
    }

    private @Nullable RevObject getObject(@Nullable NodeRef node, ObjectStore source) {
        if (node == null) {
            return null;
        }
        RevObject obj = source.get(node.getObjectId());
        return obj;
    }

    private Map<String, NodeRef[]> resolveChangedPaths(RevTree left, RevTree right) {
        if (left.equals(right)) {
            return Collections.emptyMap();
        }
        // figure out if left and right are feature trees or root trees
        CompletableFuture<Set<NodeRef>> l = findTypeTrees(left, leftSource);
        CompletableFuture<Set<NodeRef>> r = findTypeTrees(right, rightSource);
        CompletableFuture.allOf(l, r).join();

        Set<NodeRef> leftnodes = l.join();
        Set<NodeRef> rightnodes = r.join();

//        final MapDifference<String, NodeRef> difference = difference(
//                uniqueIndex(leftnodes, NodeRef::path), uniqueIndex(rightnodes, NodeRef::path));

        //NodeRef::path, but friendly for Fortify
        Function<NodeRef, String> fn_path =  new Function<NodeRef, String>() {
            @Override
            public String apply(NodeRef noderef) {
                return noderef.path();
            }};


        final MapDifference<String, NodeRef> difference =  difference(
                        uniqueIndex(leftnodes, fn_path),
                        uniqueIndex(rightnodes, fn_path)
                );

        Map<String, NodeRef[]> result = new HashMap<>();

        difference.entriesOnlyOnLeft().forEach((k, v) -> result.put(k, new NodeRef[] { v, null }));
        difference.entriesOnlyOnRight().forEach((k, v) -> result.put(k, new NodeRef[] { null, v }));
        difference.entriesDiffering()
                .forEach((k, v) -> result.put(k, new NodeRef[] { v.leftValue(), v.rightValue() }));

        return result;
    }

    private CompletableFuture<Set<NodeRef>> findTypeTrees(RevTree left, ObjectStore store) {
        return CompletableFuture.supplyAsync(() -> {
            List<NodeRef> treeNodes;
            treeNodes = command(FindFeatureTypeTrees.class).setRootTree(left).setSource(store)
                    .call();
            return new HashSet<>(treeNodes);
        });
    }

    private RevTree resolveTree(RevObject o, ObjectStore source) {
        if (o instanceof RevTree) {
            return (RevTree) o;
        }
        if (o instanceof RevCommit) {
            return source.getTree(((RevCommit) o).getTreeId());
        }
        if (o instanceof RevTag) {
            return resolveTree(source.getCommit(((RevTag) o).getCommitId()), source);
        }
        throw new IllegalArgumentException("Object does not resolve to a tree");
    }

    private RevObject resolve(@Nullable String refSpec, @Nullable RevTree tree,
            @NonNull ObjectStore source) throws NoSuchElementException {

        if (tree == null) {
            Optional<RevObject> obj = command(RevObjectParse.class).setSource(source)
                    .setRefSpec(refSpec).call();
            if (!obj.isPresent()) {
                try {
                    ObjectId id = ObjectId.valueOf(refSpec);
                    if (id.isNull() || RevTree.EMPTY_TREE_ID.equals(id)) {
                        return RevTree.EMPTY;
                    }
                } catch (IllegalArgumentException ignore) {
                    //
                }
                throw new NoSuchElementException(
                        String.format("%s does not resolve to an object", refSpec));
            }
            return obj.get();
        }
        return tree;
    }
}
