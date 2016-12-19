/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static org.locationtech.geogig.model.RevObject.TYPE.COMMIT;
import static org.locationtech.geogig.model.RevObject.TYPE.FEATURE;
import static org.locationtech.geogig.model.RevObject.TYPE.FEATURETYPE;
import static org.locationtech.geogig.model.RevObject.TYPE.TAG;
import static org.locationtech.geogig.model.RevObject.TYPE.TREE;

import java.util.List;
import java.util.SortedMap;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.HashObjectFunnels;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.hash.Funnel;
import com.google.common.hash.Hasher;

/**
 * Hashes a RevObject and returns the ObjectId.
 * 
 * @see RevObject
 * @see ObjectId#HASH_FUNCTION
 */
public class HashObject extends AbstractGeoGigOp<ObjectId> {

    @SuppressWarnings("unchecked")
    private static final Funnel<? extends RevObject>[] FUNNELS = new Funnel[RevObject.TYPE
            .values().length];
    static {
        FUNNELS[COMMIT.value()] = HashObjectFunnels.commitFunnel();
        FUNNELS[TREE.value()] = HashObjectFunnels.treeFunnel();
        FUNNELS[FEATURE.value()] = HashObjectFunnels.featureFunnel();
        FUNNELS[TAG.value()] = HashObjectFunnels.tagFunnel();
        FUNNELS[FEATURETYPE.value()] = HashObjectFunnels.featureTypeFunnel();
    }

    private RevObject object;

    /**
     * @param object {@link RevObject} to hash.
     * @return {@code this}
     */
    public HashObject setObject(RevObject object) {
        this.object = object;
        return this;
    }

    /**
     * Hashes a RevObject using a SHA1 hasher.
     * 
     * @return a new ObjectId created from the hash of the RevObject.
     */
    @Override
    protected ObjectId _call() {
        Preconditions.checkState(object != null, "Object has not been set.");

        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        @SuppressWarnings("unchecked")
        final Funnel<RevObject> funnel = (Funnel<RevObject>) FUNNELS[object.getType().value()];
        funnel.funnel(object, hasher);
        final byte[] rawKey = hasher.hash().asBytes();
        final ObjectId id = ObjectId.createNoClone(rawKey);

        return id;
    }

    public static ObjectId hashFeature(List<Object> values) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();

        HashObjectFunnels.feature(hasher, values);

        final byte[] rawKey = hasher.hash().asBytes();
        final ObjectId id = ObjectId.createNoClone(rawKey);

        return id;
    }

    public static ObjectId hashTree(@Nullable List<Node> trees, @Nullable List<Node> features,
            @Nullable SortedMap<Integer, Bucket> buckets) {

        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        trees = trees == null ? ImmutableList.of() : trees;
        features = features == null ? ImmutableList.of() : features;
        buckets = buckets == null ? ImmutableSortedMap.of() : buckets;
        HashObjectFunnels.tree(hasher, trees, features, buckets);

        final byte[] rawKey = hasher.hash().asBytes();
        final ObjectId id = ObjectId.createNoClone(rawKey);

        return id;
    }

    public static ObjectId hashTree(Optional<ImmutableList<Node>> trees,
            Optional<ImmutableList<Node>> features,
            Optional<ImmutableSortedMap<Integer, Bucket>> buckets) {

        return hashTree(trees.or(ImmutableList.of()), features.or(ImmutableList.of()),
                buckets.or(ImmutableSortedMap.of()));
    }
}
