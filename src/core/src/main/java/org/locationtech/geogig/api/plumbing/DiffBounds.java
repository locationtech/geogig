/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Jillian Crossley (Cornell University) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Bounded;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.diff.DiffSummary;
import org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.api.plumbing.diff.PathFilteringDiffConsumer;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.opengis.feature.type.FeatureType;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Computes the bounds of the difference between the two trees instead of the actual diffs.
 * 
 */
public class DiffBounds extends AbstractGeoGigOp<DiffSummary<BoundingBox, BoundingBox>> {

    private String oldVersion;

    private String newVersion;

    private boolean cached;

    private List<String> pathFilters;

    private CoordinateReferenceSystem crs;

    public DiffBounds setOldVersion(String oldVersion) {
        this.oldVersion = oldVersion;
        this.pathFilters = ImmutableList.of();
        return this;
    }

    public DiffBounds setNewVersion(String newVersion) {
        this.newVersion = newVersion;
        return this;
    }

    public DiffBounds setCompareIndex(boolean cached) {
        this.cached = cached;
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
        checkArgument(cached && oldVersion == null || !cached, String.format(
                "compare index allows only one revision to check against, got %s / %s", oldVersion,
                newVersion));

        checkArgument(newVersion == null || oldVersion != null,
                "If new rev spec is specified then old rev spec is mandatory");

        final String leftRefSpec = fromNullable(oldVersion).or(Ref.HEAD);
        final String rightRefSpec = fromNullable(newVersion).or(
                cached ? Ref.STAGE_HEAD : Ref.WORK_HEAD);

        RevTree left = resolveTree(leftRefSpec);
        RevTree right = resolveTree(rightRefSpec);

        ObjectDatabase leftSource = resolveSafeDb(leftRefSpec);
        ObjectDatabase rightSource = resolveSafeDb(rightRefSpec);
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, leftSource, rightSource);
        CoordinateReferenceSystem crs = resolveCrs();
        BoundsWalk walk = new BoundsWalk(crs, stagingDatabase());
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
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return defaultCrs;
    }

    /**
     * If {@code refSpec} can easily be determined to be on the object database (e.g. its a ref),
     * then returns the repository object database, otherwise the staging database, just to be safe
     */
    private ObjectDatabase resolveSafeDb(String refSpec) {
        Optional<Ref> ref = command(RefParse.class).setName(refSpec).call();
        if (ref.isPresent()) {
            ObjectId id = ref.get().getObjectId();
            return objectDatabase().exists(id) ? objectDatabase() : stagingDatabase();
        }
        return stagingDatabase();
    }

    private RevTree resolveTree(String refSpec) {

        Optional<ObjectId> id = command(ResolveTreeish.class).setTreeish(refSpec).call();
        Preconditions.checkState(id.isPresent(), "%s did not resolve to a tree", refSpec);

        return stagingDatabase().getTree(id.get());
    }

    private static class BoundsWalk implements PreOrderDiffWalk.Consumer {

        private DiffSummary<BoundingBox, BoundingBox> result;

        private ReferencedEnvelope leftEnv;

        private ReferencedEnvelope rightEnv;

        private final CoordinateReferenceSystem crs;

        private final ReferencedEnvelope leftHelper, rightHelper;

        private final ObjectDatabase source;

        private final Map<ObjectId, MathTransform> transformsByMetadataId;

        private Optional<ObjectId> currentDefaultLefMetadataId = Optional.absent();

        private Optional<ObjectId> currentDefaultRightMetadataId = Optional.absent();

        public BoundsWalk(CoordinateReferenceSystem crs, ObjectDatabase source) {
            this.crs = crs;
            this.source = source;
            this.transformsByMetadataId = Maps.newHashMap();
            leftEnv = new ReferencedEnvelope(this.crs);
            rightEnv = new ReferencedEnvelope(this.crs);
            leftHelper = new ReferencedEnvelope(this.crs);
            rightHelper = new ReferencedEnvelope(this.crs);
        }

        @Override
        public void feature(@Nullable Node left, @Nullable Node right) {
            setEnv(left, leftHelper, md(left).or(currentDefaultLefMetadataId));
            setEnv(right, rightHelper, md(right).or(currentDefaultRightMetadataId));
            if (!leftHelper.equals(rightHelper)) {
                leftEnv.expandToInclude(leftHelper);
                rightEnv.expandToInclude(rightHelper);
            }
        }

        @Override
        public boolean tree(@Nullable Node left, @Nullable Node right) {
            Optional<ObjectId> leftMd = md(left);
            Optional<ObjectId> rightMd = md(right);
            if (leftMd.isPresent()) {
                currentDefaultLefMetadataId = leftMd;
            }
            if (rightMd.isPresent()) {
                currentDefaultRightMetadataId = rightMd;
            }
            setEnv(left, leftHelper, leftMd.or(leftMd));
            setEnv(right, rightHelper, rightMd.or(rightMd));
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

        private Optional<ObjectId> md(@Nullable Node node) {
            return null == node ? Optional.<ObjectId> absent() : node.getMetadataId();
        }

        @Override
        public boolean bucket(final int bucketIndex, final int bucketDepth, @Nullable Bucket left,
                @Nullable Bucket right) {
            setEnv(left, leftHelper, currentDefaultLefMetadataId);
            setEnv(right, rightHelper, currentDefaultRightMetadataId);
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

        private void setEnv(@Nullable Bounded bounded, ReferencedEnvelope env,
                Optional<ObjectId> metadataId) {
            env.setToNull();
            if (bounded == null) {
                return;
            }
            bounded.expand(env);
            if (env.isNull()) {
                return;
            }
            ObjectId mdid;
            if (metadataId.isPresent()) {
                mdid = metadataId.get();
                MathTransform transform = getMathTransform(mdid);
                if (transform.isIdentity()) {
                    return;
                }
                Envelope targetEnvelope = new ReferencedEnvelope(crs);
                try {
                    int densifyPoints = isPoint(env) ? 1 : 5;
                    JTS.transform(env, targetEnvelope, transform, densifyPoints);
                    env.init(targetEnvelope);
                } catch (TransformException e) {
                    throw Throwables.propagate(e);
                }
            }
        }

        private boolean isPoint(Envelope env) {
            return env.getWidth() == 0D && env.getHeight() == 0D;
        }

        private MathTransform getMathTransform(ObjectId mdid) {
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
                    throw Throwables.propagate(e);
                }
                this.transformsByMetadataId.put(mdid, transform);
            }
            return transform;
        }

        @Override
        public void endTree(Node left, Node right) {
            String name = left == null ? right.getName() : left.getName();
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
                this.result = new DiffSummary<BoundingBox, BoundingBox>(lbounds, rbounds, merged);
            }
        }

        @Override
        public void endBucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
            // nothing to do
        }

        public DiffSummary<BoundingBox, BoundingBox> getResult() {
            DiffSummary<BoundingBox, BoundingBox> r = this.result;
            if (r == null) {
                BoundingBox empty = new ReferencedEnvelope(crs);
                r = new DiffSummary<BoundingBox, BoundingBox>(empty, empty, empty);
            }
            return r;
        }

    }
}
