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

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.diff.DiffSummary;
import org.locationtech.geogig.plumbing.diff.PathFilteringDiffConsumer;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.type.FeatureType;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Computes the bounds of the difference between the two trees instead of the actual diffs.
 * 
 */
public class DiffBounds extends AbstractGeoGigOp<DiffSummary<BoundingBox, BoundingBox>> {

    private ObjectStore leftSource, rightSource;

    private String oldVersion, newVersion;

    private RevTree oldTree, newTree;

    private List<String> pathFilters = ImmutableList.of();

    private CoordinateReferenceSystem crs;

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
            this.pathFilters = ImmutableList.of();
        } else {
            this.pathFilters = ImmutableList.copyOf(pathFilters);
        }
        return this;
    }

    /**
     * @param crs the CRS to compute the bounds in. Defaults to {@code EPSG:4326} with long/lat axis
     *        order if not set.
     */
    public DiffBounds setCRS(@Nullable CoordinateReferenceSystem crs) {
        this.crs = crs;
        return this;
    }

    @Override
    protected DiffSummary<BoundingBox, BoundingBox> _call() {
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
        CoordinateReferenceSystem crs = resolveCrs();
        BoundsWalk walk = new BoundsWalk(crs, leftSource, rightSource);
        PreOrderDiffWalk.Consumer consumer = walk;
        if (!pathFilters.isEmpty()) {
            consumer = new PathFilteringDiffConsumer(pathFilters, walk);
        }
        visitor.walk(consumer);
        DiffSummary<BoundingBox, BoundingBox> diffBounds = walk.getResult();
        return diffBounds;
    }

    private CoordinateReferenceSystem resolveCrs() {
        if (this.crs != null) {
            return this.crs;
        }
        CoordinateReferenceSystem defaultCrs;
        try {
            defaultCrs = CRS.decode("EPSG:4326", true);
        } catch (FactoryException e) {
            throw new RuntimeException(e);
        }
        return defaultCrs;
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
         * Private extension of {@link ReferencedEnvelope} to make only the methods
         * {@link BoundsWalk} is intereseted in synchronized.
         *
         */
        private static class ThreadSafeReferencedEnvelope extends ReferencedEnvelope {

            private static final long serialVersionUID = -9218780157089328231L;

            public ThreadSafeReferencedEnvelope(CoordinateReferenceSystem crs) {
                super(crs);
            }

            @Override
            public synchronized void expandToInclude(Envelope other) {
                super.expandToInclude(other);
            }

            @Override
            public synchronized void expandToInclude(double x, double y) {
                super.expandToInclude(x, y);
            }
        }

        @Nullable
        private DiffSummary<BoundingBox, BoundingBox> diffBoundsResult;

        @NonNull
        private ThreadSafeReferencedEnvelope leftEnv;

        @NonNull
        private ThreadSafeReferencedEnvelope rightEnv;

        private final CoordinateReferenceSystem crs;

        private final ObjectStore leftSource, rightSource;

        private final ConcurrentMap<ObjectId, MathTransform> transformsByMetadataId;

        public BoundsWalk(CoordinateReferenceSystem crs, ObjectStore leftSource,
                ObjectStore rightSource) {
            this.crs = crs;
            this.leftSource = leftSource;
            this.rightSource = rightSource;
            this.transformsByMetadataId = new ConcurrentHashMap<>();
            leftEnv = new ThreadSafeReferencedEnvelope(this.crs);
            rightEnv = new ThreadSafeReferencedEnvelope(this.crs);
        }

        @Override
        public boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
            ReferencedEnvelope leftHelper = getEnv(left, leftSource);
            ReferencedEnvelope rightHelper = getEnv(right, rightSource);

            if (!leftHelper.equals(rightHelper)) {
                leftEnv.expandToInclude(leftHelper);
                rightEnv.expandToInclude(rightHelper);
            }
            return true;
        }

        @Override
        public boolean tree(@Nullable NodeRef left, @Nullable NodeRef right) {
            ReferencedEnvelope leftHelper = getEnv(left, leftSource);
            ReferencedEnvelope rightHelper = getEnv(right, rightSource);

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

        @Override
        public void endTree(NodeRef left, NodeRef right) {
            String name = left == null ? right.name() : left.name();
            if (NodeRef.ROOT.equals(name)) {
                BoundingBox lbounds = new ReferencedEnvelope(this.leftEnv);
                BoundingBox rbounds = new ReferencedEnvelope(this.rightEnv);
                BoundingBox merged;
                if (lbounds.isEmpty()) {
                    merged = rbounds;
                } else if (rbounds.isEmpty()) {
                    merged = lbounds;
                } else {
                    merged = new ReferencedEnvelope(lbounds);
                    merged.include(rbounds);
                }
                this.diffBoundsResult = new DiffSummary<BoundingBox, BoundingBox>(lbounds, rbounds,
                        merged);
            }
        }

        @Override
        public boolean bucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
                @Nullable Bucket left, @Nullable Bucket right) {

            ReferencedEnvelope leftHelper = getEnv(left, leftParent, leftSource);
            ReferencedEnvelope rightHelper = getEnv(right, rightParent, rightSource);

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

        private ObjectId md(@Nullable NodeRef node) {
            return null == node ? ObjectId.NULL : node.getMetadataId();
        }

        private ReferencedEnvelope getEnv(@Nullable NodeRef ref, ObjectStore source) {
            return getEnv(ref, ref, source);
        }

        private ReferencedEnvelope getEnv(@Nullable Bounded bounded, NodeRef ref,
                ObjectStore source) {

            ObjectId metadataId = md(ref);
            ReferencedEnvelope env = new ReferencedEnvelope(this.crs);
            if (bounded != null) {
                bounded.expand(env);
                if (!env.isNull() && !metadataId.isNull()) {
                    MathTransform transform = getMathTransform(metadataId, source);
                    if (!transform.isIdentity()) {
                        Envelope targetEnvelope = new ReferencedEnvelope(crs);
                        try {
                            int densifyPoints = isPoint(env) ? 1 : 5;
                            JTS.transform(env, targetEnvelope, transform, densifyPoints);
                            env.init(targetEnvelope);
                        } catch (TransformException e) {
                            throw new RuntimeException(e);
                        }
                    }

                }
            }
            return env;
        }

        private boolean isPoint(Envelope env) {
            return env.getWidth() == 0D && env.getHeight() == 0D;
        }

        private MathTransform getMathTransform(ObjectId mdid, ObjectStore source) {
            MathTransform transform = this.transformsByMetadataId.get(mdid);
            if (transform == null) {
                RevFeatureType revtype = source.getFeatureType(mdid);
                FeatureType type = revtype.type();
                CoordinateReferenceSystem sourceCrs = type.getCoordinateReferenceSystem();
                CoordinateReferenceSystem targetCrs = this.crs;
                if (sourceCrs == null) {
                    sourceCrs = targetCrs;
                }
                try {
                    boolean lenient = true;
                    transform = CRS.findMathTransform(sourceCrs, targetCrs, lenient);
                } catch (FactoryException e) {
                    throw new RuntimeException(e);
                }
                this.transformsByMetadataId.putIfAbsent(mdid, transform);
            }
            return transform;
        }

        public DiffSummary<BoundingBox, BoundingBox> getResult() {
            DiffSummary<BoundingBox, BoundingBox> r = this.diffBoundsResult;
            if (r == null) {
                BoundingBox empty = new ReferencedEnvelope(crs);
                r = new DiffSummary<BoundingBox, BoundingBox>(empty, empty, empty);
            }
            return r;
        }

    }

    public DiffBounds setCompareIndex(boolean cached) {
        this.compareStaged = cached;
        return this;
    }

}
