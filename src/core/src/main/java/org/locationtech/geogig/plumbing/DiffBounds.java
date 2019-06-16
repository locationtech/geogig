/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Jillian Crossley (Cornell University) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.crs.CoordinateReferenceSystem;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.diff.DiffSummary;
import org.locationtech.geogig.plumbing.diff.PathFilteringDiffConsumer;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import lombok.NonNull;

/**
 * Computes the bounds of the difference between the two trees instead of the actual diffs.
 * 
 */
public class DiffBounds extends AbstractGeoGigOp<DiffSummary<Envelope, Envelope>> {

    private ObjectStore leftSource, rightSource;

    private String oldVersion, newVersion;

    private RevTree oldTree, newTree;

    private List<String> pathFilters = Collections.emptyList();

    private boolean compareStaged;

    public DiffBounds setLeftSource(ObjectStore leftSource) {
        this.leftSource = leftSource;
        return this;
    }

    public DiffBounds setRightSource(ObjectStore rightSource) {
        this.rightSource = rightSource;
        return this;
    }

    public DiffBounds setOldVersion(String oldVersion) {
        this.oldVersion = oldVersion;
        return this;
    }

    public DiffBounds setOldVersion(RevTree oldVersion) {
        this.oldTree = oldVersion;
        return this;
    }

    public DiffBounds setNewVersion(String newVersion) {
        this.newVersion = newVersion;
        return this;
    }

    public DiffBounds setNewVersion(RevTree newVersion) {
        this.newTree = newVersion;
        return this;
    }

    public DiffBounds setPathFilters(@Nullable final List<String> pathFilters) {
        if (null == pathFilters) {
            this.pathFilters = Collections.emptyList();
        } else {
            this.pathFilters = ImmutableList.copyOf(pathFilters);
        }
        return this;
    }

    /**
     * @deprecated coordinate reprojection no longer supported at this stage
     * @param crs the CRS to compute the bounds in. Defaults to {@code EPSG:4326} with long/lat axis
     *        order if not set.
     */
    public DiffBounds setCRS(@Nullable CoordinateReferenceSystem crs) {
        return this;
    }

    protected @Override DiffSummary<Envelope, Envelope> _call() {
        checkArgument(compareStaged && oldVersion == null && oldTree == null || !compareStaged,
                String.format(
                        "compare index allows only one revision to check against, got %s / %s",
                        oldVersion, newVersion));
        if (compareStaged) {
            oldVersion = Ref.HEAD;
            newVersion = Ref.STAGE_HEAD;
        } else {
            oldVersion = oldVersion == null ? (oldTree == null ? Ref.HEAD : null) : oldVersion;
            newVersion = newVersion == null ? (newTree == null ? Ref.WORK_HEAD : null) : newVersion;
        }

        ObjectStore leftSource = this.leftSource == null ? objectDatabase() : this.leftSource;
        ObjectStore rightSource = this.rightSource == null ? objectDatabase() : this.rightSource;
        RevTree left = resolveTree(oldTree, oldVersion, leftSource);
        RevTree right = resolveTree(newTree, newVersion, rightSource);

        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);
        BoundsWalk walk = new BoundsWalk(leftSource, rightSource);
        PreOrderDiffWalk.Consumer consumer = walk;
        if (!pathFilters.isEmpty()) {
            consumer = new PathFilteringDiffConsumer(pathFilters, walk);
        }
        visitor.walk(consumer);
        DiffSummary<Envelope, Envelope> diffBounds = walk.getResult();
        return diffBounds;
    }

    private RevTree resolveTree(@Nullable RevTree tree, @Nullable String refSpec,
            ObjectStore source) {
        if (tree == null) {
            Optional<ObjectId> id = command(ResolveTreeish.class).setSource(source)
                    .setTreeish(refSpec).call();
            Preconditions.checkState(id.isPresent(), "%s did not resolve to a tree", refSpec);
            tree = source.getTree(id.get());
        }
        return tree;
    }

    private static class BoundsWalk extends PreOrderDiffWalk.AbstractConsumer {

        /**
         * Private extension of {@link Envelope} to make only the methods {@link BoundsWalk} is
         * intereseted in synchronized.
         *
         */
        private static class ThreadSafeEnvelope extends Envelope {

            private static final long serialVersionUID = -9218780157089328231L;

            public @Override synchronized void expandToInclude(Envelope other) {
                super.expandToInclude(other);
            }

            public @Override synchronized void expandToInclude(double x, double y) {
                super.expandToInclude(x, y);
            }
        }

        @Nullable
        private DiffSummary<Envelope, Envelope> diffBoundsResult;

        private ThreadSafeEnvelope leftEnv;

        private ThreadSafeEnvelope rightEnv;

        private final ObjectStore leftSource, rightSource;

        public BoundsWalk(@NonNull ObjectStore leftSource, @NonNull ObjectStore rightSource) {
            this.leftSource = leftSource;
            this.rightSource = rightSource;
            leftEnv = new ThreadSafeEnvelope();
            rightEnv = new ThreadSafeEnvelope();
        }

        public @Override boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
            Envelope leftHelper = getEnv(left, leftSource);
            Envelope rightHelper = getEnv(right, rightSource);

            if (!leftHelper.equals(rightHelper)) {
                leftEnv.expandToInclude(leftHelper);
                rightEnv.expandToInclude(rightHelper);
            }
            return true;
        }

        public @Override boolean tree(@Nullable NodeRef left, @Nullable NodeRef right) {
            Envelope leftHelper = getEnv(left, leftSource);
            Envelope rightHelper = getEnv(right, rightSource);

            if (leftHelper.isNull() && rightHelper.isNull()) {
                return false;
            }

            if (leftHelper.isNull()) {
                rightEnv.expandToInclude(rightHelper);
                return false;
            } else if (rightHelper.isNull()) {
                leftEnv.expandToInclude(leftHelper);
                return false;
            }
            return true;
        }

        public @Override void endTree(NodeRef left, NodeRef right) {
            String name = left == null ? right.name() : left.name();
            if (NodeRef.ROOT.equals(name)) {
                Envelope lbounds = new Envelope(this.leftEnv);
                Envelope rbounds = new Envelope(this.rightEnv);
                Envelope merged;
                if (lbounds.isNull()) {
                    merged = rbounds;
                } else if (rbounds.isNull()) {
                    merged = lbounds;
                } else {
                    merged = new Envelope(lbounds);
                    merged.expandToInclude(rbounds);
                }
                this.diffBoundsResult = new DiffSummary<Envelope, Envelope>(lbounds, rbounds,
                        merged);
            }
        }

        public @Override boolean bucket(NodeRef leftParent, NodeRef rightParent,
                BucketIndex bucketIndex, @Nullable Bucket left, @Nullable Bucket right) {

            Envelope leftHelper = getEnv(left, leftParent, leftSource);
            Envelope rightHelper = getEnv(right, rightParent, rightSource);

            if (leftHelper.isNull() && rightHelper.isNull()) {
                return false;
            }

            if (leftHelper.isNull()) {
                rightEnv.expandToInclude(rightHelper);
                return false;
            } else if (rightHelper.isNull()) {
                leftEnv.expandToInclude(leftHelper);
                return false;
            }
            return true;
        }

        private Envelope getEnv(@Nullable NodeRef ref, ObjectStore source) {
            return getEnv(ref, ref, source);
        }

        private Envelope getEnv(@Nullable Bounded bounded, NodeRef ref, ObjectStore source) {
            Envelope env = new Envelope();
            if (bounded != null) {
                bounded.expand(env);
            }
            return env;
        }

        public DiffSummary<Envelope, Envelope> getResult() {
            DiffSummary<Envelope, Envelope> r = this.diffBoundsResult;
            if (r == null) {
                Envelope empty = new Envelope();
                r = new DiffSummary<Envelope, Envelope>(empty, empty, empty);
            }
            return r;
        }

    }

    public DiffBounds setCompareIndex(boolean cached) {
        this.compareStaged = cached;
        return this;
    }

}
