/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.merge;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * This operation merges two features that have compatible changes, returning the result of this
 * automatic merging. Features must have the same schema
 * 
 * No checking is performed to see that changes are actually compatible, so this should be done in
 * advance. If that's not the case, the merged result might have lost some changes made on one of
 * the features to merge, which will be overwritten by changes in the other one
 * 
 */
public class MergeFeaturesOp extends AbstractGeoGigOp<Feature> {

    private static final Logger LOG = LoggerFactory.getLogger(MergeFeaturesOp.class);

    private NodeRef nodeRefB;

    private NodeRef nodeRefA;

    private NodeRef ancestorRef;

    @Override
    protected Feature _call() {
        checkArgument(nodeRefA != null, "first feature version not specified");
        checkArgument(nodeRefB != null, "second feature version not specified");
        checkArgument(ancestorRef != null, "ancestor version not specified");

        checkArgument(nodeRefA.path().equals(nodeRefB.path()),
                "old and new versions do not correspond to the same feature");
        checkCompatibleFeatureTypes(ancestorRef, nodeRefA, nodeRefB);

        final Map<ObjectId, RevObject> objects = getObjects(ancestorRef, nodeRefA, nodeRefB);

        RevFeature featureA = (RevFeature) objects.get(nodeRefA.getObjectId());
        RevFeature featureB = (RevFeature) objects.get(nodeRefB.getObjectId());
        RevFeature ancestorFeature = (RevFeature) objects.get(ancestorRef.getObjectId());
        RevFeatureType featureType = (RevFeatureType) objects.get(nodeRefA.getMetadataId());

        try {
            return merge(featureA, featureB, ancestorFeature, featureType);
        } catch (RuntimeException e) {
            LOG.error("Error merging feature base: {}, left: {}, right: {}", ancestorRef, nodeRefA,
                    nodeRefB, e);
            throw e;
        }
    }

    private Map<ObjectId, RevObject> getObjects(NodeRef ancestorRef, NodeRef nodeRefA,
            NodeRef nodeRefB) {

        final ObjectId metadataId = ancestorRef.getMetadataId();
        final ObjectId ancestorFeatureId = ancestorRef.getObjectId();
        final ObjectId featureAId = nodeRefA.getObjectId();
        final ObjectId featureBId = nodeRefB.getObjectId();
        Iterable<ObjectId> ids = ImmutableList.of(metadataId, ancestorFeatureId, featureAId,
                featureBId);
        Iterator<RevObject> objsit = objectDatabase().getAll(ids, BulkOpListener.NOOP_LISTENER);

        //RevObject::getId, but friendly for Fortify
        Function<RevObject, ObjectId> fn_getId =  new Function<RevObject, ObjectId>() {
            @Override
            public ObjectId apply(RevObject revobj) {
                return revobj.getId();
            }};

        ImmutableMap<ObjectId, RevObject> map = Maps.uniqueIndex(objsit, fn_getId);
        checkState(map.containsKey(metadataId), "Invalid reference: %s", metadataId);
        checkState(map.containsKey(ancestorFeatureId), "Invalid reference: %s", ancestorFeatureId);
        checkState(map.containsKey(featureAId), "Invalid reference: %s", featureAId);
        checkState(map.containsKey(featureBId), "Invalid reference: %s", featureBId);
        return map;
    }

    private void checkCompatibleFeatureTypes(NodeRef ancestorRef, NodeRef nodeRefA,
            NodeRef nodeRefB) {

        checkArgument(ancestorRef.getMetadataId().equals(nodeRefA.getMetadataId()),
                "Non-matching feature types. Cannot merge");
        checkArgument(ancestorRef.getMetadataId().equals(nodeRefB.getMetadataId()),
                "Non-matching feature types. Cannot merge");
    }

    private Feature merge(RevFeature featureA, RevFeature featureB, RevFeature ancestor,
            RevFeatureType featureType) {

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(
                (SimpleFeatureType) featureType.type());
        ImmutableList<PropertyDescriptor> descriptors = featureType.descriptors();
        for (int i = 0; i < descriptors.size(); i++) {
            final PropertyDescriptor descriptor = descriptors.get(i);
            final boolean isGeom = descriptor instanceof GeometryDescriptor;
            Name name = descriptor.getName();
            Object valueAncestor = ancestor.get(i).orNull();
            Object valueA = featureA.get(i).orNull();
            Object valueB = featureB.get(i).orNull();

            final boolean valueAEqualsAncestor = valueEquals(isGeom, valueA, valueAncestor);

            if (valueAEqualsAncestor) {
                featureBuilder.set(name, valueB);
            } else {
                Object merged = valueA;
                featureBuilder.set(name, merged);
            }
        }
        return featureBuilder.buildFeature(nodeRefA.name());

    }

    private boolean valueEquals(boolean isGeom, @Nullable Object v1, @Nullable Object v2) {
        return isGeom ? geomEquals((Geometry) v1, (Geometry) v2) : Objects.equals(v1, v2);
    }

    private boolean geomEquals(@Nullable Geometry g1, @Nullable Geometry g2) {
        if (g1 == null || g2 == null) {
            return g1 == null && g2 == null;
        }
        return g1.equalsExact(g2);
    }

    public MergeFeaturesOp setFirstFeature(NodeRef feature) {
        this.nodeRefA = feature;
        return this;
    }

    public MergeFeaturesOp setSecondFeature(NodeRef feature) {
        this.nodeRefB = feature;
        return this;
    }

    public MergeFeaturesOp setAncestorFeature(NodeRef ancestor) {
        this.ancestorRef = ancestor;
        return this;
    }

}
