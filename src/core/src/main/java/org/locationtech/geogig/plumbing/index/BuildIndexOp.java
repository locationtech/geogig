/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.index;

import static com.google.common.base.Preconditions.checkState;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.PropertyDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

/**
 * Builds an index tree for the given canonical tree.
 */
public class BuildIndexOp extends AbstractGeoGigOp<RevTree> {

    private static final Logger log = LoggerFactory.getLogger(BuildIndexOp.class);

    private IndexInfo index;

    private RevTree oldCanonicalTree;

    private RevTree newCanonicalTree;

    private ObjectId revFeatureTypeId;

    /**
     * @param index the {@link IndexInfo} to use
     * @return {@code this}
     */
    public BuildIndexOp setIndex(IndexInfo index) {
        this.index = index;
        return this;
    }

    /**
     * @param oldTree the previous canonical tree, used to optimize the construction of a new
     *        indexed tree
     * @return {@code this}
     */
    public BuildIndexOp setOldCanonicalTree(RevTree oldTree) {
        this.oldCanonicalTree = oldTree;
        return this;
    }

    /**
     * @param newTree the canonical tree to build an index for
     * @return {@code this}
     */
    public BuildIndexOp setNewCanonicalTree(RevTree newTree) {
        this.newCanonicalTree = newTree;
        return this;
    }

    /**
     * @param id the {@link ObjectId} of the feature type
     * @return
     */
    public BuildIndexOp setRevFeatureTypeId(ObjectId id) {
        this.revFeatureTypeId = id;
        return this;
    }

    /**
     * Performs the operation.
     * 
     * @return the {@link RevTree} that represents the indexed version of the canonical tree
     */
    @Override
    protected RevTree _call() {
        checkState(index != null, "index to update was not provided");
        checkState(oldCanonicalTree != null, "old canonical version of the tree was not provided");
        checkState(newCanonicalTree != null, "new canonical version of the tree was not provided");
        checkState(revFeatureTypeId != null, "FeatureType id was not provided");

        final RevTreeBuilder builder = resolveTreeBuilder();
        final ProgressListener progress = getProgressListener();
        final PreOrderDiffWalk.Consumer builderConsumer = resolveConsumer(builder, progress);

        boolean preserveIterationOrder = true;
        final ObjectDatabase canonicalStore = objectDatabase();
        PreOrderDiffWalk walk = new PreOrderDiffWalk(oldCanonicalTree, newCanonicalTree,
                canonicalStore, canonicalStore, preserveIterationOrder);

        final Stopwatch dagTime = Stopwatch.createStarted();
        walk.walk(builderConsumer);
        dagTime.stop();

        if (progress.isCanceled()) {
            return null;
        }

        progress.setDescription(
                String.format("Index updated in %s. Building final tree...", dagTime));

        final Stopwatch revTreeTime = Stopwatch.createStarted();
        final RevTree indexTree;
        try {
            indexTree = builder.build(() -> progress.isCanceled());
        } catch (Exception e) {
            log.error("Error building index", e);
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
        revTreeTime.stop();

        if (progress.isCanceled()) {
            return null;
        }

        final long canonicalSize = newCanonicalTree.size();
        final long indexSize = indexTree.size();
        checkState(canonicalSize == indexSize,
                "ERROR: Canonical tree size: %s (%s), Index tree size: %s (%s)",
                newCanonicalTree.size(), newCanonicalTree.getId(), indexTree.size(),
                indexTree.getId());

        indexDatabase().addIndexedTree(index, newCanonicalTree.getId(), indexTree.getId());
        progress.setDescription(String.format("QuadTree created. Size: %,d, time: %s",
                indexTree.size(), revTreeTime));

        progress.complete();

        return indexTree;

    }

    private Consumer resolveConsumer(RevTreeBuilder builder,
            final ProgressListener progressListener) {
        final Set<String> attNames = IndexInfo.getMaterializedAttributeNames(index);

        final boolean isMaterialized = !attNames.isEmpty();
        final Consumer consumer;

        if (isMaterialized) {
            Map<String, Integer> extraDataProperties = attributeIndexMapping(attNames);

            consumer = new MaterializedBuilderConsumer(builder, objectDatabase(),
                    extraDataProperties, progressListener);
        } else {
            consumer = new SimpleTreeBuilderConsumer(builder, progressListener);
        }

        return consumer;
    }

    private Map<String, Integer> attributeIndexMapping(Set<String> attNames) {
        Map<String, Integer> attToValueIndex = new HashMap<>();

        RevFeatureType featureType = objectDatabase().getFeatureType(revFeatureTypeId);
        for (String attName : attNames) {
            int attIndex = indexOf(attName, featureType);
            attToValueIndex.put(attName, Integer.valueOf(attIndex));
        }

        return attToValueIndex;
    }

    private int indexOf(String attName, RevFeatureType featureType) {
        ImmutableList<PropertyDescriptor> descriptors = featureType.descriptors();
        for (int i = 0; i < descriptors.size(); i++) {
            String name = descriptors.get(i).getName().getLocalPart();
            if (attName.equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException(String.format("Feature type %s has no attribute '%s'",
                featureType.getName().getLocalPart(), attName));
    }

    private RevTreeBuilder resolveTreeBuilder() {
        final IndexDatabase indexDatabase = indexDatabase();

        final RevTree oldIndexTree;
        if (oldCanonicalTree.isEmpty()) {
            oldIndexTree = RevTree.EMPTY;
        } else {
            final Optional<ObjectId> oldIndexTreeId = indexDatabase.resolveIndexedTree(index,
                    oldCanonicalTree.getId());
            if (oldIndexTreeId.isPresent()) {
                oldIndexTree = indexDatabase.getTree(oldIndexTreeId.get());
            } else {
                oldIndexTree = RevTree.EMPTY;
            }
        }

        final IndexType indexType = index.getIndexType();
        final RevTreeBuilder builder;
        switch (indexType) {
        case QUADTREE:
            final Envelope maxBounds = IndexInfo.getMaxBounds(index);
            checkState(null != maxBounds, "QuadTree index does not contain max bounds");

            ObjectStore source = indexDatabase();
            ObjectStore target = source;
            builder = RevTreeBuilder.quadBuilder(source, target, oldIndexTree, maxBounds);
            break;
        default:
            throw new UnsupportedOperationException("Uknown index type: " + indexType);
        }
        return builder;
    }
}
