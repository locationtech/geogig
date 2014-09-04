/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.merge;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.diff.GeometryAttributeDiff;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.vividsolutions.jts.geom.Geometry;

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

    private NodeRef nodeRefB;

    private NodeRef nodeRefA;

    private NodeRef ancestorRef;

    @Override
    protected  Feature _call() {
        checkNotNull(nodeRefA, "first feature version not specified");
        checkNotNull(nodeRefB, "second feature version not specified");
        checkNotNull(ancestorRef, "ancestor version not specified");
        // String firstPath = removeRef(oldNodeRef.path());
        // String newPath = removeRef(newNodeRef.path());
        checkArgument(nodeRefA.path().equals(nodeRefB.path()),
                "old and new versions do not correspond to the same feature");

        Optional<RevFeature> featureA = command(RevObjectParse.class).setObjectId(
                nodeRefA.getNode().getObjectId()).call(RevFeature.class);
        checkArgument(featureA.isPresent(), "Invalid reference: %s", nodeRefA);

        Optional<RevFeature> featureB = command(RevObjectParse.class).setObjectId(
                nodeRefB.getNode().getObjectId()).call(RevFeature.class);
        checkArgument(featureB.isPresent(), "Invalid reference: %s", nodeRefB);

        Optional<RevFeature> ancestorFeature = command(RevObjectParse.class).setObjectId(
                ancestorRef.getNode().getObjectId()).call(RevFeature.class);
        checkArgument(ancestorFeature.isPresent(), "Invalid reference: %s", ancestorRef);

        Optional<RevFeatureType> featureTypeA = command(RevObjectParse.class).setObjectId(
                nodeRefA.getMetadataId()).call(RevFeatureType.class);
        checkArgument(featureTypeA.isPresent(), "Invalid reference: %s", nodeRefA);

        Optional<RevFeatureType> featureTypeB = command(RevObjectParse.class).setObjectId(
                nodeRefB.getMetadataId()).call(RevFeatureType.class);
        checkArgument(featureTypeB.isPresent(), "Invalid reference: %s", nodeRefB);

        Optional<RevFeatureType> ancestorFeatureType = command(RevObjectParse.class).setObjectId(
                ancestorRef.getMetadataId()).call(RevFeatureType.class);
        checkArgument(ancestorFeatureType.isPresent(), "Invalid reference: %s", ancestorRef);

        Preconditions.checkArgument(
                featureTypeA.equals(featureTypeB) && featureTypeA.equals(ancestorFeatureType),
                "Non-matching feature types. Cannot merge");

        return merge(featureA.get(), featureB.get(), ancestorFeature.get(),
                ancestorFeatureType.get());

    }

    @SuppressWarnings("unchecked")
    private Feature merge(RevFeature featureA, RevFeature featureB, RevFeature ancestor,
            RevFeatureType featureType) {

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(
                (SimpleFeatureType) featureType.type());
        ImmutableList<Optional<Object>> valuesA = featureA.getValues();
        ImmutableList<Optional<Object>> valuesB = featureB.getValues();
        ImmutableList<Optional<Object>> valuesAncestor = ancestor.getValues();
        ImmutableList<PropertyDescriptor> descriptors = featureType.sortedDescriptors();
        for (int i = 0; i < descriptors.size(); i++) {
            PropertyDescriptor descriptor = descriptors.get(i);
            boolean isGeom = Geometry.class.isAssignableFrom(descriptor.getType().getBinding());
            Name name = descriptor.getName();
            Optional<Object> valueAncestor = valuesAncestor.get(i);
            Optional<Object> valueA = valuesA.get(i);
            Optional<Object> valueB = valuesB.get(i);
            if (!valueA.equals(valueAncestor)) {
                Optional<Object> merged = valueA;
                if (isGeom && !valueB.equals(valueAncestor)) { // true merge is only done with
                                                               // geometries
                    GeometryAttributeDiff diffB = new GeometryAttributeDiff(
                            Optional.fromNullable((Geometry) valueAncestor.orNull()),
                            Optional.fromNullable((Geometry) valueB.orNull()));
                    merged = (Optional<Object>) diffB.applyOn(valueA);
                }
                featureBuilder.set(name, merged.orNull());
            } else {
                featureBuilder.set(name, valueB.orNull());
            }
        }
        return featureBuilder.buildFeature(nodeRefA.name());

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
