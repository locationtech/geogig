/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.data.Query;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.operation.transform.IdentityTransform;
import org.geotools.renderer.ScreenMap;
import org.junit.Test;
import org.locationtech.geogig.data.FindFeatureTypeTrees;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.index.CreateQuadTree;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.identity.ResourceId;
import org.opengis.filter.sort.SortBy;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Polygon;

public class GeoGigFeatureSourceTest extends RepositoryTestCase {

    private static final FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(null);

    private GeoGigDataStore dataStore;

    private SimpleFeatureSource pointsSource;

    private SimpleFeatureSource linesSource;

    @Override
    protected void setUpInternal() throws Exception {
        dataStore = new GeoGigDataStore(geogig.getRepository());
        dataStore.createSchema(super.pointsType);
        dataStore.createSchema(super.linesType);
        insertAndAdd(points1, points2, points3, lines1, lines2, lines3);
        geogig.command(CommitOp.class).setAuthor("yo", "yo@test.com")
                .setCommitter("me", "me@test.com").setMessage("initial import").call();

        pointsSource = dataStore.getFeatureSource(pointsName);
        linesSource = dataStore.getFeatureSource(linesName);
    }

    @Override
    protected void tearDownInternal() throws Exception {
        dataStore.dispose();
        dataStore = null;
        pointsSource = null;
        linesSource = null;
    }

    @Test
    public void testGetName() {
        assertEquals(pointsName, pointsSource.getName().getLocalPart());
        assertEquals(linesName, linesSource.getName().getLocalPart());
    }

    @Test
    public void testGetInfo() {
        assertNotNull(pointsSource.getInfo());
        assertNotNull(pointsSource.getInfo().getBounds());
        assertNotNull(pointsSource.getInfo().getCRS());
        assertEquals(pointsName, pointsSource.getInfo().getName());

        assertNotNull(linesSource.getInfo());
        assertNotNull(linesSource.getInfo().getBounds());
        assertNotNull(linesSource.getInfo().getCRS());
        assertEquals(linesName, linesSource.getInfo().getName());
    }

    @Test
    public void testGetDataStore() {
        assertSame(dataStore, pointsSource.getDataStore());
        assertSame(dataStore, linesSource.getDataStore());
    }

    @Test
    public void testGetQueryCapabilities() {
        assertNotNull(pointsSource.getQueryCapabilities());

        assertFalse(pointsSource.getQueryCapabilities().isJoiningSupported());

        assertTrue(pointsSource.getQueryCapabilities().isOffsetSupported());

        assertTrue(pointsSource.getQueryCapabilities().isReliableFIDSupported());

        // TODO: add this ability back
        // assertTrue(pointsSource.getQueryCapabilities().isUseProvidedFIDSupported());

        SortBy[] sortAttributes = { SortBy.NATURAL_ORDER };
        assertTrue(pointsSource.getQueryCapabilities().supportsSorting(sortAttributes));
    }

    @Test
    public void testGetSchema() {
        assertEquals(pointsType, pointsSource.getSchema());
        assertEquals(linesType, linesSource.getSchema());
    }

    @Test
    public void testGetBounds() throws IOException {
        ReferencedEnvelope expected;
        ReferencedEnvelope bounds;

        bounds = pointsSource.getBounds();
        assertNotNull(bounds);
        expected = boundsOf(points1, points2, points3);
        assertEquals(expected, bounds);

        bounds = linesSource.getBounds();
        assertNotNull(bounds);
        expected = boundsOf(lines1, lines2, lines3);
        assertEquals(expected, bounds);
    }

    @Test
    public void testGetBoundsQuery() throws Exception {

        ReferencedEnvelope bounds;
        Filter filter;

        filter = ff.id(Collections.singleton(ff.featureId(RepositoryTestCase.idP2)));
        bounds = pointsSource.getBounds(new Query(pointsName, filter));
        assertEquals(boundsOf(points2), bounds);

        ReferencedEnvelope queryBounds = boundsOf(points1, points2);

        Polygon geometry = JTS.toGeometry(queryBounds);
        filter = ff.intersects(ff.property(pointsType.getGeometryDescriptor().getLocalName()),
                ff.literal(geometry));

        bounds = pointsSource.getBounds(new Query(pointsName, filter));
        assertEquals(boundsOf(points1, points2), bounds);

        ReferencedEnvelope transformedQueryBounds;
        CoordinateReferenceSystem queryCrs = CRS.decode("EPSG:3857");
        transformedQueryBounds = queryBounds.transform(queryCrs, true);

        geometry = JTS.toGeometry(transformedQueryBounds);
        geometry.setUserData(queryCrs);

        filter = ff.intersects(ff.property(pointsType.getGeometryDescriptor().getLocalName()),
                ff.literal(geometry));

        bounds = pointsSource.getBounds(new Query(pointsName, filter));
        assertEquals(boundsOf(points1, points2), bounds);

        filter = ECQL.toFilter("sp = 'StringProp2_3' OR ip = 2000");
        bounds = linesSource.getBounds(new Query(linesName, filter));
        assertEquals(boundsOf(lines3, lines2), bounds);
    }

    @Test
    public void testGetBoundsQueryWithSpatialIndex() throws Exception {

        createQuadTree(pointsName);
        ReferencedEnvelope bounds;
        Filter filter;

        filter = ff.id(Collections.singleton(ff.featureId(RepositoryTestCase.idP2)));
        bounds = pointsSource.getBounds(new Query(pointsName, filter));
        assertEquals(boundsOf(points2), bounds);

        ReferencedEnvelope queryBounds = boundsOf(points1, points2);

        Polygon geometry = JTS.toGeometry(queryBounds);
        filter = ff.intersects(ff.property(pointsType.getGeometryDescriptor().getLocalName()),
                ff.literal(geometry));

        bounds = pointsSource.getBounds(new Query(pointsName, filter));
        assertEquals(boundsOf(points1, points2), bounds);

        ReferencedEnvelope transformedQueryBounds;
        CoordinateReferenceSystem queryCrs = CRS.decode("EPSG:3857");
        transformedQueryBounds = queryBounds.transform(queryCrs, true);

        geometry = JTS.toGeometry(transformedQueryBounds);
        geometry.setUserData(queryCrs);

        filter = ff.intersects(ff.property(pointsType.getGeometryDescriptor().getLocalName()),
                ff.literal(geometry));

        bounds = pointsSource.getBounds(new Query(pointsName, filter));
        assertEquals(boundsOf(points1, points2), bounds);

        filter = ECQL.toFilter("sp = 'StringProp2_3' OR ip = 2000");
        bounds = linesSource.getBounds(new Query(linesName, filter));
        assertEquals(boundsOf(lines3, lines2), bounds);
    }

    @Test
    public void testGetCount() throws Exception {
        assertEquals(3, pointsSource.getCount(Query.ALL));
        assertEquals(3, linesSource.getCount(Query.ALL));

        Filter filter;

        filter = ff.id(Collections.singleton(ff.featureId(RepositoryTestCase.idP2)));
        assertEquals(1, pointsSource.getCount(new Query(pointsName, filter)));

        ReferencedEnvelope queryBounds = boundsOf(points1, points2);

        Polygon geometry = JTS.toGeometry(queryBounds);
        filter = ff.intersects(ff.property(pointsType.getGeometryDescriptor().getLocalName()),
                ff.literal(geometry));

        assertEquals(2, pointsSource.getCount(new Query(pointsName, filter)));

        ReferencedEnvelope transformedQueryBounds;
        CoordinateReferenceSystem queryCrs = CRS.decode("EPSG:3857");
        transformedQueryBounds = queryBounds.transform(queryCrs, true);

        geometry = JTS.toGeometry(transformedQueryBounds);
        geometry.setUserData(queryCrs);

        filter = ff.intersects(ff.property(pointsType.getGeometryDescriptor().getLocalName()),
                ff.literal(geometry));

        assertEquals(2, pointsSource.getCount(new Query(pointsName, filter)));

        filter = ECQL.toFilter("sp = 'StringProp2_3' OR ip = 2000");
        assertEquals(2, linesSource.getCount(new Query(linesName, filter)));
    }

    @Test
    public void testGetFeatures() throws Exception {
        SimpleFeatureCollection collection;
        Set<List<Object>> actual;
        Set<List<Object>> expected;

        collection = pointsSource.getFeatures();
        assertEquals(pointsType, collection.getSchema());

        actual = Sets.newHashSet();
        for (Feature f : toList(collection)) {
            SimpleFeature sf = (SimpleFeature) f;
            actual.add(sf.getAttributes());
        }

        expected = ImmutableSet.of(((SimpleFeature) points1).getAttributes(),
                ((SimpleFeature) points2).getAttributes(),
                ((SimpleFeature) points3).getAttributes());

        assertEquals(expected, actual);

        collection = linesSource.getFeatures();
        assertEquals(linesType, collection.getSchema());

        actual = Sets.newHashSet();
        for (Feature f : toList(collection)) {
            actual.add(((SimpleFeature) f).getAttributes());
        }

        expected = ImmutableSet.of(((SimpleFeature) lines1).getAttributes(),
                ((SimpleFeature) lines2).getAttributes(), ((SimpleFeature) lines3).getAttributes());

        assertEquals(expected, actual);
    }

    @Test
    public void testGetFeaturesFilter() throws Exception {
        SimpleFeatureCollection collection;
        Set<List<Object>> actual;
        Set<List<Object>> expected;

        Filter filter;

        filter = ff.id(Collections.singleton(ff.featureId(RepositoryTestCase.idP2)));
        collection = pointsSource.getFeatures(new Query(pointsName, filter));
        actual = Sets.newHashSet();
        for (SimpleFeature f : toList(collection)) {
            actual.add(f.getAttributes());
        }
        expected = Collections.singleton(((SimpleFeature) points2).getAttributes());

        assertEquals(expected, actual);

        ReferencedEnvelope queryBounds = boundsOf(points1, points2);

        Polygon geometry = JTS.toGeometry(queryBounds);
        filter = ff.intersects(ff.property(pointsType.getGeometryDescriptor().getLocalName()),
                ff.literal(geometry));

        collection = pointsSource.getFeatures(new Query(pointsName, filter));
        actual = Sets.newHashSet();
        for (SimpleFeature f : toList(collection)) {
            actual.add(f.getAttributes());
        }
        expected = ImmutableSet.of(((SimpleFeature) points1).getAttributes(),
                ((SimpleFeature) points2).getAttributes());

        assertEquals(expected, actual);

        ReferencedEnvelope transformedQueryBounds;
        CoordinateReferenceSystem queryCrs = CRS.decode("EPSG:3857");
        transformedQueryBounds = queryBounds.transform(queryCrs, true);

        geometry = JTS.toGeometry(transformedQueryBounds);
        geometry.setUserData(queryCrs);

        filter = ff.intersects(ff.property(pointsType.getGeometryDescriptor().getLocalName()),
                ff.literal(geometry));

        collection = pointsSource.getFeatures(new Query(pointsName, filter));
        actual = Sets.newHashSet();
        for (SimpleFeature f : toList(collection)) {
            actual.add(f.getAttributes());
        }
        expected = ImmutableSet.of(((SimpleFeature) points1).getAttributes(),
                ((SimpleFeature) points2).getAttributes());

        assertEquals(expected.size(), actual.size());
        assertEquals(expected, actual);

        filter = ECQL.toFilter("sp = 'StringProp2_3' OR ip = 2000");
        collection = linesSource.getFeatures(new Query(linesName, filter));
        actual = Sets.newHashSet();
        for (SimpleFeature f : toList(collection)) {
            actual.add(f.getAttributes());
        }
        expected = ImmutableSet.of(((SimpleFeature) lines2).getAttributes(),
                ((SimpleFeature) lines3).getAttributes());

        assertEquals(expected, actual);

    }

    @Test
    public void testFeatureIdsAreVersioned() throws IOException {
        SimpleFeatureCollection collection = pointsSource.getFeatures(Query.ALL);
        SimpleFeatureIterator features = collection.features();

        Set<FeatureId> ids = Sets.newHashSet();
        try {
            while (features.hasNext()) {
                SimpleFeature next = features.next();
                FeatureId identifier = next.getIdentifier();
                ids.add(identifier);
            }
        } finally {
            features.close();
        }

        List<NodeRef> refs = toList(repo.command(LsTreeOp.class).setReference(pointsName)
                .setStrategy(Strategy.FEATURES_ONLY).call());

        assertEquals(3, refs.size());

        Map<String, NodeRef> expected = new HashMap<String, NodeRef>();
        for (NodeRef ref : refs) {
            expected.put(ref.path(), ref);
        }

        for (FeatureId id : ids) {
            assertFalse("ResourceId is a query object", id instanceof ResourceId);
            assertNotNull(id.getID());
            assertNotNull(id + " has no featureVersion set", id.getFeatureVersion());
            NodeRef ref = expected.get(id.getID());
            assertNotNull(ref);
            assertEquals(ref.getObjectId().toString(), id.getFeatureVersion());
        }
    }

    @Test
    public void testScreenMap() throws Exception {
        // Test a single point to make sure the feature tree itself (with the same bounds as the
        // point it contains) doesn't write to the ScreenMap
        deleteAndAdd(points2);
        deleteAndAdd(points3);
        geogig.command(CommitOp.class).setMessage("drop to 1 point").call();
        Query query = new Query(pointsName);
        ScreenMap screenMap = new ScreenMap(-180, -90, 360, 180);
        screenMap.setSpans(1.0, 1.0);
        screenMap.setTransform(IdentityTransform.create(2));

        query.getHints().put(Hints.SCREENMAP, screenMap);

        SimpleFeatureIterator iter = pointsSource.getFeatures(query).features();

        assertTrue(iter.hasNext());
        assertEquals(points1.getIdentifier().getID(), iter.next().getID());

        assertTrue(screenMap.get(boundsOf(points1)));
    }

    private List<SimpleFeature> toList(SimpleFeatureCollection collection) {
        List<SimpleFeature> features = Lists.newArrayList();
        SimpleFeatureIterator iterator = collection.features();
        try {
            while (iterator.hasNext()) {
                features.add(iterator.next());
            }
        } finally {
            iterator.close();
        }
        return features;
    }

    private void createQuadTree(String tree) throws IOException {

        Map<String, NodeRef> all = Maps.uniqueIndex(
                geogig.command(FindFeatureTypeTrees.class).setRootTreeRef("HEAD").call(),
                (r) -> r.path());
        NodeRef typeTreeRef = all.get(tree);
        assertNotNull(typeTreeRef);
        CreateQuadTree command = geogig.command(CreateQuadTree.class);
        command.setTypeTreeRef(typeTreeRef);
        command.call();
    }

}
