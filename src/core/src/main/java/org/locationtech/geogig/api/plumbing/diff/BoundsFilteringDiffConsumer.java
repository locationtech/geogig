/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.diff;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultEngineeringCRS;
import org.locationtech.geogig.api.Bounded;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;

/**
 * A {@link Consumer} decorator that filters {@link Node nodes} by a bounding box intersection check
 * before delegating.
 */
public final class BoundsFilteringDiffConsumer extends PreOrderDiffWalk.ForwardingConsumer {

    private DiffPathTracker tracker = new DiffPathTracker();

    private final ReferencedEnvelope boundsFilter;

    private ObjectDatabase ftypeSource;

    public BoundsFilteringDiffConsumer(ReferencedEnvelope bounds,
            PreOrderDiffWalk.Consumer delegate, ObjectDatabase ftypeSource) {
        super(delegate);
        this.boundsFilter = bounds;
        this.ftypeSource = ftypeSource;
    }

    @Override
    public boolean tree(Node left, Node right) {
        tracker.tree(left, right);
        if (intersects(left, right)) {
            return super.tree(left, right);
        }
        return false;
    }

    @Override
    public void endTree(Node left, Node right) {
        if (intersects(left, right)) {
            super.endTree(left, right);
        }
        tracker.endTree(left, right);
    }

    @Override
    public boolean bucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
        if (intersects(left, right)) {
            return super.bucket(bucketIndex, bucketDepth, left, right);
        }
        return false;
    }

    @Override
    public void endBucket(int bucketIndex, int bucketDepth, Bucket left, Bucket right) {
        if (intersects(left, right)) {
            super.endBucket(bucketIndex, bucketDepth, left, right);
        }
    }

    @Override
    public void feature(Node left, Node right) {
        if (intersects(left, right)) {
            super.feature(left, right);
        }
    }

    private boolean intersects(Bounded left, Bounded right) {
        return intersects(left, tracker.currentLeftMetadataId())
                || intersects(right, tracker.currentRightMetadataId());
    }

    private boolean intersects(@Nullable Bounded node, final Optional<ObjectId> metadataId) {
        if (node == null) {
            return false;
        }
        if (!metadataId.isPresent()) {
            return true;
        }
        ReferencedEnvelope nativeCrsFilter = getProjectedFilter(metadataId.get());
        boolean intersects = node.intersects(nativeCrsFilter);
        return intersects;
    }

    private Map<ObjectId, ReferencedEnvelope> filtersByMetadataId = new HashMap<>();

    private ReferencedEnvelope getProjectedFilter(final ObjectId metadataId) {
        ReferencedEnvelope projectedFilter = filtersByMetadataId.get(metadataId);
        if (projectedFilter == null) {
            projectedFilter = createProjectedFilter(metadataId);
            filtersByMetadataId.put(metadataId, projectedFilter);
        }
        return projectedFilter;
    }

    private ReferencedEnvelope createProjectedFilter(ObjectId metadataId) {
        final ReferencedEnvelope boundsFilter = this.boundsFilter;
        RevFeatureType featureType = ftypeSource.getFeatureType(metadataId);
        CoordinateReferenceSystem nativeCrs = featureType.type().getCoordinateReferenceSystem();
        if (null == nativeCrs || nativeCrs instanceof DefaultEngineeringCRS) {
            return boundsFilter;
        }
        ReferencedEnvelope transformedFilter;
        try {
            transformedFilter = boundsFilter.transform(nativeCrs, true);
        } catch (TransformException | FactoryException e) {
            throw Throwables.propagate(e);
        }
        return transformedFilter;
    }

}
