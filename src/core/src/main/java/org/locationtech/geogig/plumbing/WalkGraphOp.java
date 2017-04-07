/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public class WalkGraphOp extends AbstractGeoGigOp<Void> {

    private String reference;

    private Listener listener;

    public static interface Listener {

        public void featureType(RevFeatureType ftype);

        public void commit(RevCommit commit);

        public void feature(final NodeRef featureNode);

        public void starTree(final NodeRef treeNode);

        public void endTree(final NodeRef treeNode);

        public void bucket(final BucketIndex bucketIndex, final Bucket bucket);

        public void endBucket(final BucketIndex bucketIndex, final Bucket bucket);
    }

    public WalkGraphOp setListener(final Listener listener) {
        this.listener = listener;
        return this;
    }

    public WalkGraphOp setReference(final String reference) {
        this.reference = reference;
        return this;
    }

    @Override
    protected Void _call() {
        Preconditions.checkState(reference != null, "Reference not provided");
        Preconditions.checkState(listener != null, "Listener not provided");

        Optional<ObjectId> oid = command(RevParse.class).setRefSpec(reference).call();

        ObjectStore source = objectDatabase();
        if (!oid.isPresent()) {
            source = indexDatabase();
            oid = command(RevParse.class).setRefSpec(reference).setSource(source).call();
        }

        Preconditions.checkArgument(oid.isPresent(), "Can't resolve reference '%s' at %s",
                reference, repository().getLocation());

        RevTree left = RevTree.EMPTY;
        RevTree right;

        RevObject revObject = source.get(oid.get());
        Preconditions.checkArgument(revObject instanceof RevCommit || revObject instanceof RevTree,
                "'%s' can't be resolved to a tree: %s", reference, revObject.getType());

        if (revObject instanceof RevCommit) {
            RevCommit c = (RevCommit) revObject;
            listener.commit(c);
            right = source.getTree(c.getTreeId());
        } else {
            right = (RevTree) revObject;
        }

        PreOrderDiffWalk walk = new PreOrderDiffWalk(left, right, source, source, true);
        
        final ObjectStore treeSource = source;
        Consumer consumer = new Consumer() {

            private WalkGraphOp.Listener listener = WalkGraphOp.this.listener;

            private final ObjectStore odb = objectDatabase();

            // used to report feature types only once
            private Set<ObjectId> visitedTypes = new HashSet<ObjectId>();

            @Override
            public boolean tree(@Nullable NodeRef left, @Nullable NodeRef right) {
                if (!right.getMetadataId().isNull()) {
                    ObjectId featureTypeId = right.getMetadataId();
                    if (!visitedTypes.contains(featureTypeId)) {
                        visitedTypes.add(featureTypeId);
                        listener.featureType(odb.getFeatureType(featureTypeId));
                        if (!featureTypeId.isNull()) {
                            checkExists(featureTypeId, featureTypeId, odb);
                        }
                    }
                }
                listener.starTree(right);
                checkExists(right.getObjectId(), right, treeSource);
                return true;
            }

            @Override
            public boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
                listener.feature(right);
                checkExists(right.getObjectId(), right, odb);
                return true;
            }

            @Override
            public void endTree(@Nullable NodeRef left, @Nullable NodeRef right) {
                listener.endTree(right);
            }

            @Override
            public boolean bucket(NodeRef lp, NodeRef rp, BucketIndex bucketIndex,
                    @Nullable Bucket left, @Nullable Bucket right) {

                listener.bucket(bucketIndex, right);
                checkExists(right.getObjectId(), right, treeSource);
                return true;
            }

            @Override
            public void endBucket(NodeRef lp, NodeRef rp, BucketIndex bucketIndex,
                    @Nullable Bucket left, @Nullable Bucket right) {

                listener.endBucket(bucketIndex, right);
            }

            private void checkExists(ObjectId id, Object o, ObjectStore store) {
                if (!store.exists(id)) {
                    throw new IllegalStateException("Object " + o + " not found.");
                }
            }

        };
        walk.walk(consumer);
        return null;
    }
}
