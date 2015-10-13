/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public class WalkGraphOp extends AbstractGeoGigOp<Void> {

    private String reference;

    private Listener listener;

    public static interface Listener {

        public void featureType(RevFeatureType ftype);

        public void commit(RevCommit commit);

        public void feature(final Node featureNode);

        public void starTree(final Node treeNode);

        public void endTree(final Node treeNode);

        public void bucket(final int bucketIndex, final int bucketDepth, final Bucket bucket);

        public void endBucket(final int bucketIndex, final int bucketDepth, final Bucket bucket);
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

        final ObjectDatabase odb = objectDatabase();

        Optional<ObjectId> oid = command(RevParse.class).setRefSpec(reference).call();
        Preconditions.checkArgument(oid.isPresent(), "Can't resolve reference '%s'", reference);

        RevTree left = RevTree.EMPTY;
        RevTree right;

        RevObject revObject = odb.get(oid.get());
        Preconditions.checkArgument(revObject instanceof RevCommit || revObject instanceof RevTree,
                "'%s' can't be resolved to a tree: %s", reference, revObject.getType());

        if (revObject instanceof RevCommit) {
            RevCommit c = (RevCommit) revObject;
            listener.commit(c);
            right = odb.getTree(c.getTreeId());
        } else {
            right = (RevTree) revObject;
        }

        PreOrderDiffWalk walk = new PreOrderDiffWalk(left, right, odb, odb);
        Consumer consumer = new Consumer() {

            private WalkGraphOp.Listener listener = WalkGraphOp.this.listener;

            private final ObjectDatabase odb = objectDatabase();

            // used to report feature types only once
            private Set<ObjectId> visitedTypes = new HashSet<ObjectId>();

            @Override
            public boolean tree(@Nullable Node left, @Nullable Node right) {
                if (right.getMetadataId().isPresent()) {
                    ObjectId featureTypeId = right.getMetadataId().get();
                    if (!visitedTypes.contains(featureTypeId)) {
                        visitedTypes.add(featureTypeId);
                        listener.featureType(odb.getFeatureType(featureTypeId));
                        if (!featureTypeId.isNull()) {
                            checkExists(featureTypeId, right);
                        }
                    }
                }
                listener.starTree(right);
                checkExists(right.getObjectId(), right);
                return true;
            }

            @Override
            public boolean feature(@Nullable Node left, @Nullable Node right) {
                listener.feature(right);
                checkExists(right.getObjectId(), right);
                return true;
            }

            @Override
            public void endTree(@Nullable Node left, @Nullable Node right) {
                listener.endTree(right);
            }

            @Override
            public boolean bucket(int bucketIndex, int bucketDepth, @Nullable Bucket left,
                    @Nullable Bucket right) {
                listener.bucket(bucketIndex, bucketDepth, right);
                checkExists(right.getObjectId(), right);
                return true;
            }

            @Override
            public void endBucket(int bucketIndex, int bucketDepth, @Nullable Bucket left,
                    @Nullable Bucket right) {
                listener.endBucket(bucketIndex, bucketDepth, right);
            }

            private void checkExists(ObjectId id, Object o) {
                if (!odb.exists(id)) {
                    throw new IllegalStateException("Object " + o + " not found.");
                }
            }

        };
        walk.walk(consumer);
        return null;
    }
}
