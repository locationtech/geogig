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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.locationtech.geogig.model.RevObject.TYPE.COMMIT;
import static org.locationtech.geogig.model.RevObject.TYPE.FEATURE;
import static org.locationtech.geogig.model.RevObject.TYPE.FEATURETYPE;
import static org.locationtech.geogig.model.RevObject.TYPE.TAG;
import static org.locationtech.geogig.model.RevObject.TYPE.TREE;

import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.HashObjectFunnels;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.opengis.feature.type.FeatureType;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Funnel;
import com.google.common.hash.Hasher;
import com.google.common.hash.PrimitiveSink;

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
        final ObjectId id = ObjectId.create(rawKey);

        return id;
    }

    public static ObjectId hashFeature(List<Object> values) {
        return hash(h -> HashObjectFunnels.feature(h, values));
    }

    @Deprecated
    public static ObjectId hashTree(@Nullable List<Node> trees, @Nullable List<Node> features,
            @Nullable SortedMap<Integer, Bucket> buckets) {

        final List<Node> t = trees == null ? ImmutableList.of() : trees;
        final List<Node> f = features == null ? ImmutableList.of() : features;
        final Iterable<Bucket> b = buckets == null ? Collections.emptySet() : buckets.values();

        return hash(h -> HashObjectFunnels.tree(h, t, f, b));

    }

    public static ObjectId hashTree(@Nullable List<Node> trees, @Nullable List<Node> features,
            @Nullable Iterable<Bucket> buckets) {

        final List<Node> t = trees == null ? ImmutableList.of() : trees;
        final List<Node> f = features == null ? ImmutableList.of() : features;
        final Iterable<Bucket> b = buckets == null ? Collections.emptySet() : buckets;

        return hash(h -> HashObjectFunnels.tree(h, t, f, b));

    }

    public static ObjectId hashTag(String name, ObjectId commitId, String message,
            RevPerson tagger) {
        return hash(h -> HashObjectFunnels.tag(h, name, commitId, message, tagger));
    }

    public static ObjectId hashFeatureType(FeatureType featureType) {
        checkNotNull(featureType);
        return hash(h -> HashObjectFunnels.featureType(h, featureType));
    }

    public static ObjectId hashCommit(ObjectId treeId, List<ObjectId> parentIds, RevPerson author,
            RevPerson committer, String commitMessage) {

        return hash(h -> HashObjectFunnels.commit(h, treeId, parentIds, author, committer,
                commitMessage));
    }

    private static ObjectId hash(Consumer<PrimitiveSink> funnel) {
        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();

        funnel.accept(hasher);

        final byte[] rawKey = hasher.hash().asBytes();
        final ObjectId id = ObjectId.create(rawKey);

        return id;
    }
}
