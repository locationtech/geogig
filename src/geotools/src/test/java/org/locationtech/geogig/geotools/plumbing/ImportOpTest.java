/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.geotools.plumbing;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeSet;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.LsTreeOp;
import org.locationtech.geogig.api.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.api.plumbing.ResolveFeatureType;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.geotools.cli.porcelain.TestHelper;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

public class ImportOpTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testNullDataStore() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setTable("table1");
        exception.expect(GeoToolsOpException.class);
        importOp.call();
    }

    @Test
    public void testNullTableNotAll() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createEmptyTestFactory().createDataStore(null));
        importOp.setAll(false);
        exception.expect(GeoToolsOpException.class);
        importOp.call();
    }

    @Test
    public void testEmptyTableNotAll() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setTable("");
        importOp.setAll(false);
        importOp.setDataStore(TestHelper.createEmptyTestFactory().createDataStore(null));
        exception.expect(GeoToolsOpException.class);
        importOp.call();
    }

    @Test
    public void testEmptyTableAndAll() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setTable("");
        importOp.setAll(true);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.call();
    }

    @Test
    public void testTableAndAll() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setTable("table1");
        importOp.setAll(true);
        importOp.setDataStore(TestHelper.createEmptyTestFactory().createDataStore(null));
        exception.expect(GeoToolsOpException.class);
        importOp.call();
    }

    @Test
    public void testTableNotFound() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createEmptyTestFactory().createDataStore(null));
        importOp.setAll(false);
        importOp.setTable("table1");
        exception.expect(GeoToolsOpException.class);
        importOp.call();
    }

    @Test
    public void testNoFeaturesFound() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createEmptyTestFactory().createDataStore(null));
        importOp.setAll(true);
        exception.expect(GeoToolsOpException.class);
        importOp.call();
    }

    @Test
    public void testTypeNameException() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createFactoryWithGetNamesException().createDataStore(null));
        importOp.setAll(false);
        importOp.setTable("table1");
        exception.expect(GeoToolsOpException.class);
        importOp.call();
    }

    @Test
    public void testGetFeatureSourceException() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createFactoryWithGetFeatureSourceException()
                .createDataStore(null));
        importOp.setAll(false);
        importOp.setTable("table1");
        exception.expect(GeoToolsOpException.class);
        importOp.call();
    }

    @Test
    public void testImportTable() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.setAll(false);
        importOp.setTable("table1");

        RevTree newWorkingTree = importOp.call();
        Optional<NodeRef> ref = geogig.command(FindTreeChild.class).setParent(newWorkingTree)
                .setChildPath("table1/feature1").setIndex(true).call();
        assertTrue(ref.isPresent());

        ref = geogig.command(FindTreeChild.class).setParent(newWorkingTree)
                .setChildPath("table1/feature2").setIndex(true).call();
        assertTrue(ref.isPresent());
    }

    @Test
    public void testImportTableWithNoFeatures() throws Exception {

        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.setAll(false);
        importOp.setTable("table4");
        importOp.call();

        geogig.command(AddOp.class).call();
        Optional<RevObject> ft = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:table4").call();
        assertTrue(ft.isPresent());
    }

    @Test
    public void testImportAll() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.setAll(true);

        RevTree newWorkingTree = importOp.call();
        Optional<NodeRef> ref = geogig.command(FindTreeChild.class).setParent(newWorkingTree)
                .setChildPath("table1/feature1").setIndex(true).call();
        assertTrue(ref.isPresent());

        ref = geogig.command(FindTreeChild.class).setParent(newWorkingTree)
                .setChildPath("table1/feature2").setIndex(true).call();
        assertTrue(ref.isPresent());

        ref = geogig.command(FindTreeChild.class).setParent(newWorkingTree)
                .setChildPath("table2/feature3").setIndex(true).call();
        assertTrue(ref.isPresent());
    }

    @Test
    public void testImportAllWithDifferentFeatureTypesAndDestPath() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.setAll(true);
        importOp.setDestinationPath("dest");
        importOp.setAdaptToDefaultFeatureType(false);
        importOp.call();
        Iterator<NodeRef> features = geogig.command(LsTreeOp.class)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).call();
        ArrayList<NodeRef> list = Lists.newArrayList(features);
        assertEquals(4, list.size());
        TreeSet<ObjectId> set = Sets.newTreeSet();
        for (NodeRef node : list) {
            set.add(node.getMetadataId());
        }
        assertEquals(4, set.size());
        for (ObjectId metadataId : set) {
            Optional<RevFeatureType> ft = geogig.command(RevObjectParse.class)
                    .setObjectId(metadataId).call(RevFeatureType.class);
            assertTrue(ft.isPresent());
            assertEquals("dest", ft.get().getName().getLocalPart());
        }
    }

    @Test
    public void testImportAllWithDifferentFeatureTypesAndDestPathAndAdd() throws Exception {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setCRS(CRS.decode("EPSG:4326"));
        builder.add("geom", Point.class);
        builder.add("label", String.class);
        builder.setName("dest");
        SimpleFeatureType type = builder.buildFeatureType();
        GeometryFactory gf = new GeometryFactory();
        SimpleFeature feature = SimpleFeatureBuilder.build(type,
                new Object[] { gf.createPoint(new Coordinate(0, 0)), "feature0" }, "feature");
        geogig.getRepository().workingTree().insert("dest", feature);
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.setAll(true);
        importOp.setOverwrite(false);
        importOp.setDestinationPath("dest");
        importOp.setAdaptToDefaultFeatureType(false);
        importOp.call();
        Iterator<NodeRef> features = geogig.command(LsTreeOp.class)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).call();
        ArrayList<NodeRef> list = Lists.newArrayList(features);
        assertEquals(5, list.size());
        TreeSet<ObjectId> set = Sets.newTreeSet();
        ArrayList<RevFeatureType> ftlist = new ArrayList<RevFeatureType>();
        for (NodeRef node : list) {
            Optional<RevFeatureType> ft = geogig.command(RevObjectParse.class)
                    .setObjectId(node.getMetadataId()).call(RevFeatureType.class);
            assertTrue(ft.isPresent());
            ftlist.add(ft.get());
            set.add(node.getMetadataId());
        }
        assertEquals(4, set.size());
    }

    @Test
    public void testAddUsingOriginalFeatureType() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.setTable("table1");
        importOp.call();
        importOp.setTable("table2");
        importOp.setAdaptToDefaultFeatureType(false);
        importOp.setDestinationPath("table1");
        importOp.setOverwrite(false);
        importOp.call();
        Iterator<NodeRef> features = geogig.command(LsTreeOp.class)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).call();
        ArrayList<NodeRef> list = Lists.newArrayList(features);
        assertEquals(3, list.size());
        TreeSet<ObjectId> set = Sets.newTreeSet();
        for (NodeRef node : list) {
            set.add(node.getMetadataId());
        }
        assertEquals(2, set.size());
    }

    @Test
    public void testAlter() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.setTable("table1");
        importOp.call();
        importOp.setTable("table2");
        importOp.setDestinationPath("table1");
        importOp.setAlter(true);
        importOp.call();
        Iterator<NodeRef> features = geogig.command(LsTreeOp.class)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).call();
        ArrayList<NodeRef> list = Lists.newArrayList(features);
        assertEquals(3, list.size());
        Optional<RevFeature> feature = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:table1/feature1").call(RevFeature.class);
        assertTrue(feature.isPresent());
        ImmutableList<Optional<Object>> values = feature.get().getValues();
        assertEquals(2, values.size());
        assertTrue(values.get(0).isPresent());
        assertFalse(values.get(1).isPresent());
        TreeSet<ObjectId> set = Sets.newTreeSet();
        for (NodeRef node : list) {
            set.add(node.getMetadataId());
        }
        assertEquals(1, set.size());
        Optional<RevFeatureType> featureType = geogig.command(RevObjectParse.class)
                .setObjectId(set.iterator().next()).call(RevFeatureType.class);
        assertTrue(featureType.isPresent());
        assertEquals("table1", featureType.get().getName().getLocalPart());
        assertEquals("name", featureType.get().sortedDescriptors().get(1).getName().getLocalPart());
    }

    @Test
    public void testImportWithOverriddenGeomName() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.setTable("table1");
        importOp.setGeometryNameOverride("my_geom_name");
        importOp.call();
        Iterator<NodeRef> features = geogig.command(LsTreeOp.class)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).call();
        ArrayList<NodeRef> list = Lists.newArrayList(features);
        assertEquals(2, list.size());
        Optional<RevFeatureType> featureType = geogig.command(RevObjectParse.class)
                .setObjectId(list.get(0).getMetadataId()).call(RevFeatureType.class);
        assertTrue(featureType.isPresent());
        assertEquals("table1", featureType.get().getName().getLocalPart());
        assertEquals("my_geom_name", featureType.get().sortedDescriptors().get(0).getName()
                .getLocalPart());
    }

    @Test
    public void testImportWithOverriddenGeomNameAlredyInUse() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.setTable("table1");
        importOp.setGeometryNameOverride("label");
        try {
            importOp.call();
            fail("Should throw exception complaining of parameter name already in use");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("The provided geom name is already in use"));
        }
    }

    @Test
    public void testImportWithFid() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.setTable("table3");
        importOp.setDestinationPath("table3");
        importOp.setFidAttribute("number");
        importOp.call();
        Optional<RevFeature> feature = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:table3/1000").call(RevFeature.class);
        assertTrue(feature.isPresent());
    }

    @Test
    public void testDeleteException() throws Exception {
        WorkingTree workTree = mock(WorkingTree.class);
        Context cmdl = mock(Context.class);
        when(cmdl.workingTree()).thenReturn(workTree);
        doThrow(new RuntimeException("Exception")).when(workTree).delete(any(String.class));
        ImportOp importOp = new ImportOp();
        importOp.setContext(cmdl);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.setAll(true);
        exception.expect(GeoToolsOpException.class);
        importOp.call();
    }

    @Test
    public void testAdaptFeatureType() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.setTable("shpLikeTable");
        importOp.setDestinationPath("table");
        importOp.call();
        Optional<RevFeature> feature = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:table/feature1").call(RevFeature.class);
        assertTrue(feature.isPresent());
        RevFeatureType originalFeatureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec("WORK_HEAD:table/feature1").call().get();
        importOp.setTable("shpLikeTable2");
        importOp.call();
        feature = geogig.command(RevObjectParse.class).setRefSpec("WORK_HEAD:table/feature1")
                .call(RevFeature.class);
        assertTrue(feature.isPresent());
        RevFeatureType featureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec("WORK_HEAD:table/feature1").call().get();
        assertEquals(originalFeatureType.getId(), featureType.getId());
        GeometryFactory gf = new GeometryFactory();
        ImmutableList<Optional<Object>> values = feature.get().getValues();
        assertEquals(values.get(0).get(), gf.createPoint(new Coordinate(0, 7)));
        assertEquals(values.get(1).get(), 3.2);
        assertEquals(values.get(2).get(), 1100.0);
        importOp.setTable("GeoJsonLikeTable");
        importOp.call();
        feature = geogig.command(RevObjectParse.class).setRefSpec("WORK_HEAD:table/feature1")
                .call(RevFeature.class);
        assertTrue(feature.isPresent());
        featureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec("WORK_HEAD:table/feature1").call().get();
        assertEquals(originalFeatureType.getId(), featureType.getId());
        values = feature.get().getValues();
        assertEquals(values.get(0).get(), gf.createPoint(new Coordinate(0, 8)));
        assertEquals(values.get(1).get(), 4.2);
        assertEquals(values.get(2).get(), 1200.0);
    }

    @Test
    public void testCannotAdaptFeatureTypeIfCRSChanges() throws Exception {
        ImportOp importOp = geogig.command(ImportOp.class);
        importOp.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        importOp.setTable("GeoJsonLikeTable");
        importOp.setDestinationPath("table");
        importOp.call();
        Optional<RevFeature> feature = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:table/feature1").call(RevFeature.class);
        assertTrue(feature.isPresent());
        importOp.setTable("GeoJsonLikeTable2");
        try {
            importOp.call();
            fail();
        } catch (GeoToolsOpException e) {
            assertEquals(GeoToolsOpException.StatusCode.INCOMPATIBLE_FEATURE_TYPE, e.statusCode);
        }
    }

    @Override
    protected void setUpInternal() throws Exception {
    }
}
