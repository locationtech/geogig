/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.diff;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Map;

import org.geotools.geometry.jts.WKTReader2;
import org.junit.Test;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevFeatureTypeImpl;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Geometry;

public class PatchSerializationTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Test
    public void testRemoveFeatureAttributePatch() throws Exception {
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Optional<?> oldValue = Optional.fromNullable(points1B.getProperty("extra").getValue());
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(oldValue, null);
        map.put(modifiedPointsType.getDescriptor("extra"), diff);
        FeatureDiff featureDiff = new FeatureDiff(path, map,
                RevFeatureTypeImpl.build(modifiedPointsType), RevFeatureTypeImpl.build(pointsType));
        patch.addModifiedFeature(featureDiff);
        patch.addFeatureType(RevFeatureTypeImpl.build(pointsType));
        testPatch(patch);
    }

    @Test
    public void testAddFeatureAttributePatch() throws Exception {
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Optional<?> newValue = Optional.fromNullable(points1B.getProperty("extra").getValue());
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(null, newValue);
        map.put(modifiedPointsType.getDescriptor("extra"), diff);
        FeatureDiff featureDiff = new FeatureDiff(path, map, RevFeatureTypeImpl.build(pointsType),
                RevFeatureTypeImpl.build(modifiedPointsType));
        patch.addModifiedFeature(featureDiff);
        patch.addFeatureType(RevFeatureTypeImpl.build(modifiedPointsType));
        testPatch(patch);
    }

    @Test
    public void testModifyFeatureAttributePatch() throws Exception {
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        Map<PropertyDescriptor, AttributeDiff> map = Maps.newHashMap();
        Optional<?> oldValue = Optional.fromNullable(points1.getProperty("sp").getValue());
        GenericAttributeDiffImpl diff = new GenericAttributeDiffImpl(oldValue, Optional.of("new"));
        Optional<Geometry> oldGeometry = Optional.fromNullable((Geometry) points1.getProperty("pp")
                .getValue());
        Optional<Geometry> newGeometry = Optional.of(new WKTReader2().read("POINT (2 2)"));
        GeometryAttributeDiff geomDiff = new GeometryAttributeDiff(oldGeometry, newGeometry);
        map.put(pointsType.getDescriptor("sp"), diff);
        map.put(pointsType.getDescriptor("pp"), geomDiff);
        FeatureDiff feaureDiff = new FeatureDiff(path, map, RevFeatureTypeImpl.build(pointsType),
                RevFeatureTypeImpl.build(pointsType));
        patch.addModifiedFeature(feaureDiff);
        testPatch(patch);
    }

    @Test
    public void testAddFeaturePatch() throws Exception {
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        patch.addAddedFeature(path, points1, RevFeatureTypeImpl.build(pointsType));
        testPatch(patch);
    }

    @Test
    public void testRemoveFeaturePatch() throws Exception {
        Patch patch = new Patch();
        String path = NodeRef.appendChild(pointsName, points1.getIdentifier().getID());
        patch.addRemovedFeature(path, points1, RevFeatureTypeImpl.build(pointsType));
        testPatch(patch);
    }

    @Test
    public void testModifiedEmptyFeatureTypePatch() throws Exception {
        Patch patch = new Patch();
        RevFeatureType featureType = RevFeatureTypeImpl.build(pointsType);
        RevFeatureType modifiedFeatureType = RevFeatureTypeImpl.build(modifiedPointsType);
        patch.addFeatureType(featureType);
        patch.addFeatureType(modifiedFeatureType);
        patch.addAlteredTree(new FeatureTypeDiff(pointsName, featureType.getId(),
                modifiedFeatureType.getId()));
        testPatch(patch);
    }

    @Test
    public void testAddEmptyFeatureTypePatch() throws Exception {
        Patch patch = new Patch();
        RevFeatureType featureType = RevFeatureTypeImpl.build(pointsType);
        patch.addFeatureType(featureType);
        patch.addAlteredTree(new FeatureTypeDiff(pointsName, null, featureType.getId()));
        testPatch(patch);
    }

    @Test
    public void testRemoveEmptyFeatureTypePatch() throws Exception {
        Patch patch = new Patch();
        RevFeatureType featureType = RevFeatureTypeImpl.build(pointsType);
        patch.addFeatureType(featureType);
        patch.addAlteredTree(new FeatureTypeDiff(pointsName, featureType.getId(), null));
        testPatch(patch);
    }

    private void testPatch(Patch patch) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(out, Charsets.UTF_8);
        PatchSerializer.write(writer, patch);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        Patch serializedPatch = PatchSerializer.read(reader);
        assertEquals(patch, serializedPatch);
    }

}
