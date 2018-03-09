/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultEngineeringCRS;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.storage.ObjectStore;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;

/**
 * A {@link Consumer} decorator that filters {@link Node nodes} by a bounding box intersection check
 * before delegating.
 */
public final class BoundsFilteringDiffConsumer extends PreOrderDiffWalk.ForwardingConsumer {

    private final ReferencedEnvelope boundsFilter;

    private ObjectStore ftypeSource;

    public BoundsFilteringDiffConsumer(ReferencedEnvelope bounds,
            PreOrderDiffWalk.Consumer delegate, ObjectStore ftypeSource) {
        super(delegate);
        this.boundsFilter = bounds;
        this.ftypeSource = ftypeSource;
    }

    @Override
    public boolean tree(NodeRef left, NodeRef right) {
        if (intersects(left, right)) {
            return super.tree(left, right);
        }
        return false;
    }

    @Override
    public void endTree(NodeRef left, NodeRef right) {
        if (intersects(left, right)) {
            super.endTree(left, right);
        }
    }

    @Override
    public boolean bucket(NodeRef lparent, NodeRef rparent, BucketIndex bucketIndex, Bucket left,
            Bucket right) {
        ObjectId lmd = md(lparent);
        ObjectId rmd = md(rparent);
        if (intersects(left, lmd) || intersects(right, rmd)) {
            return super.bucket(lparent, rparent, bucketIndex, left, right);
        }
        return false;
    }

    @Override
    public void endBucket(NodeRef lparent, NodeRef rparent, BucketIndex bucketIndex, Bucket left,
            Bucket right) {
        ObjectId lmd = md(lparent);
        ObjectId rmd = md(rparent);
        if (intersects(left, lmd) || intersects(right, rmd)) {
            super.endBucket(lparent, rparent, bucketIndex, left, right);
        }
    }

    @Override
    public boolean feature(NodeRef left, NodeRef right) {
        if (intersects(left, right)) {
            return super.feature(left, right);
        }
        return true;
    }

    private ObjectId md(@Nullable NodeRef ref) {
        return ref == null ? ObjectId.NULL : ref.getMetadataId();
    }

    private boolean intersects(NodeRef left, NodeRef right) {
        return intersects(left) || intersects(right);
    }

    private boolean intersects(@Nullable NodeRef node) {
        return intersects(node, node == null ? ObjectId.NULL : node.getMetadataId());
    }

    private boolean intersects(@Nullable Bounded node, final ObjectId metadataId) {
        if (node == null) {
            return false;
        }
        if (metadataId.isNull()) {
            return true;
        }
        ReferencedEnvelope nativeCrsFilter = getProjectedFilter(metadataId);
        boolean intersects = node.intersects(nativeCrsFilter);
        return intersects;
    }

    private ConcurrentMap<ObjectId, ReferencedEnvelope> filtersByMetadataId = new ConcurrentHashMap<>();

    private ReferencedEnvelope getProjectedFilter(final ObjectId metadataId) {
        ReferencedEnvelope projectedFilter = filtersByMetadataId.get(metadataId);
        if (projectedFilter == null) {
            projectedFilter = createProjectedFilter(metadataId);
            filtersByMetadataId.putIfAbsent(metadataId, projectedFilter);
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
            throw new RuntimeException(e);
        }
        return transformedFilter;
    }

}
