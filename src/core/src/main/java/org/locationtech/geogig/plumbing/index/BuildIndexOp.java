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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.QuadTreeBuilder;
import org.locationtech.geogig.model.impl.RevTreeBuilder;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.vividsolutions.jts.geom.Envelope;

public class BuildIndexOp extends AbstractGeoGigOp<RevTree> {

    private IndexInfo index;

    private RevTree oldCanonicalTree;

    private RevTree newCanonicalTree;

    private ObjectId revFeatureTypeId;

    public BuildIndexOp setIndex(IndexInfo index) {
        this.index = index;
        return this;
    }

    public BuildIndexOp setOldCanonicalTree(RevTree oldTree) {
        this.oldCanonicalTree = oldTree;
        return this;
    }

    public BuildIndexOp setNewCanonicalTree(RevTree newTree) {
        this.newCanonicalTree = newTree;
        return this;
    }

    public BuildIndexOp setRevFeatureTypeId(ObjectId id) {
        this.revFeatureTypeId = id;
        return this;
    }

    @Override
    protected RevTree _call() {
        checkState(index != null, "index to update was not provided");
        checkState(oldCanonicalTree != null, "old canonical version of the tree was not provided");
        checkState(newCanonicalTree != null, "new canonical version of the tree was not provided");
        checkState(revFeatureTypeId != null, "FeatureType id was not provided");

        final RevTreeBuilder builder = resolveTreeBuilder();
        final PreOrderDiffWalk.Consumer builderConsumer = resolveConsumer(builder);

        boolean preserveIterationOrder = true;
        final ObjectDatabase canonicalStore = objectDatabase();
        PreOrderDiffWalk walk = new PreOrderDiffWalk(oldCanonicalTree, newCanonicalTree,
                canonicalStore, canonicalStore, preserveIterationOrder);

        final ProgressListener progress = getProgressListener();
        final Stopwatch dagTime = Stopwatch.createStarted();
        walk.walk(builderConsumer);
        dagTime.stop();

        if (progress.isCanceled()) {
            return null;
        }

        progress.setDescription(
                String.format("Index updated in %s. Building final tree...", dagTime));

        final Stopwatch revTreeTime = Stopwatch.createStarted();
        RevTree indexTree;
        try {
            indexTree = builder.build();
        } catch (Exception e) {
            e.printStackTrace();
            throw Throwables.propagate(Throwables.getRootCause(e));
        }
        revTreeTime.stop();
        indexDatabase().addIndexedTree(index, newCanonicalTree.getId(), indexTree.getId());
        progress.setDescription(String.format("QuadTree created. Size: %,d, time: %s",
                indexTree.size(), revTreeTime));

        progress.complete();

        return indexTree;

    }

    private Consumer resolveConsumer(RevTreeBuilder builder) {
        final @Nullable String[] attNames = (String[]) index.getMetadata()
                .get(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA);

        final boolean isMaterialized = attNames != null && attNames.length > 0;
        final Consumer consumer;

        final ProgressListener progressListener = getProgressListener();
        if (isMaterialized) {
            Map<String, Integer> extraDataProperties = attributeIndexMapping(attNames);

            consumer = new MaterializedBuilderConsumer(builder, objectDatabase(),
                    extraDataProperties, progressListener);
        } else {
            consumer = new SimpleTreeBuilderConsumer(builder, progressListener);
        }

        return consumer;
    }

    private Map<String, Integer> attributeIndexMapping(String... attNames) {
        Map<String, Integer> attToValueIndex = new HashMap<>();

        RevFeatureType featureType = objectDatabase().getFeatureType(revFeatureTypeId);
        for (String attName : ImmutableSet.copyOf(attNames)) {
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
            checkArgument(oldIndexTreeId.isPresent(), "No index tree exists for %s",
                    oldCanonicalTree.getId());
            oldIndexTree = indexDatabase.getTree(oldIndexTreeId.get());
        }

        final IndexType indexType = index.getIndexType();
        final RevTreeBuilder builder;
        switch (indexType) {
        case QUADTREE:
            final Envelope maxBounds;
            maxBounds = (Envelope) index.getMetadata().get(IndexInfo.MD_QUAD_MAX_BOUNDS);
            checkState(null != maxBounds, "QuadTree index does not contain max bounds");

            ObjectStore source = indexDatabase();
            ObjectStore target = source;
            builder = QuadTreeBuilder.create(source, target, oldIndexTree, maxBounds);
            break;
        default:
            throw new UnsupportedOperationException("Uknown index type: " + indexType);
        }
        return builder;
    }
}
