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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.data.DataUtilities;
import org.geotools.data.Query;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
import org.geotools.feature.NameImpl;
import org.geotools.feature.visitor.CalcResult;
import org.geotools.feature.visitor.FeatureCalc;
import org.geotools.feature.visitor.MaxVisitor;
import org.geotools.feature.visitor.MinVisitor;
import org.geotools.feature.visitor.NearestVisitor;
import org.geotools.feature.visitor.UniqueVisitor;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.operation.transform.IdentityTransform;
import org.geotools.renderer.ScreenMap;
import org.junit.Test;
import org.locationtech.geogig.data.FindFeatureTypeTrees;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.internal.QuadTreeTestSupport;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.index.CreateQuadTree;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
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

public class GeoGigFeatureSourceTest extends RepositoryTestCase {

    private static final FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(null);

    private GeoGigDataStore dataStore;

    private GeogigFeatureStore pointsSource;

    private SimpleFeatureSource linesSource;

    private final String namespace = "http://geogig.org/test";

    @Override
    protected void setUpInternal() throws Exception {
        dataStore = new GeoGigDataStore(geogig.getRepository());
        dataStore.createSchema(super.pointsType);
        dataStore.createSchema(super.linesType);
        insertAndAdd(points1, points2, points3, lines1, lines2, lines3);
        geogig.command(CommitOp.class).setAuthor("yo", "yo@test.com")
                .setCommitter("me", "me@test.com").setMessage("initial import").call();

        pointsSource = (GeogigFeatureStore) dataStore.getFeatureSource(pointsName);
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
    public void testGetLocalName() {
        assertEquals(pointsName, pointsSource.getName().getLocalPart());
        assertEquals(linesName, linesSource.getName().getLocalPart());
    }

    @Test
    public void testGetName() throws Exception {
        dataStore.setNamespaceURI(namespace);
        insertAndAdd(poly1, poly2, poly3);
        commit("added polygons layer");

        ContentFeatureSource polySource = dataStore.getFeatureSource(polyName);
        Name polys = new NameImpl(namespace, polyName);
        assertEquals(polys, polySource.getName());

        assertEquals(polys, polySource.getSchema().getName());
        // testGetName(polys, polySource, new Query(polyName));
        testGetName(polys, polySource,
                new Query(polyName, Filter.INCLUDE, new String[] { "ip", "sp" }));
    }

    private void testGetName(Name expected, ContentFeatureSource source, Query query)
            throws IOException {
        SimpleFeatureCollection collection = source.getFeatures(query);
        SimpleFeatureType schema = collection.getSchema();
        assertEquals(expected, schema.getName());
        try (SimpleFeatureIterator it = collection.features()) {
            SimpleFeature f = it.next();
            assertEquals(expected, f.getName());
            assertEquals(expected, it.next().getName());
        }
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

    @Test
    public void testRespectsSuppliedGeometryFactory() throws Exception {
        SimpleFeatureSource source = this.linesSource;
        Query query = new Query();
        GeometryFactory suppliedGeomFac = new GeometryFactory(
                new PrecisionModel(PrecisionModel.FLOATING_SINGLE));
        query.getHints().put(Hints.JTS_GEOMETRY_FACTORY, suppliedGeomFac);

        SimpleFeature[] collection = (SimpleFeature[]) source.getFeatures(query).toArray();
        assertEquals(3, collection.length);
        for (SimpleFeature f : collection) {
            Geometry g = (Geometry) f.getDefaultGeometry();
            assertSame(suppliedGeomFac, g.getFactory());
        }
    }

    /**
     * Make sure a spatial query against a geometry attribute that does not define a CRS just
     * assumes the query is in the native CRS
     */
    @Test
    public void testSpatialQueryOnNullCrsAttribute() throws Exception {

        final String typeName = "nullcrs";
        final SimpleFeatureType type = DataUtilities.createType(typeName,
                "the_geom:Point,name:String");

        assertNull(type.getGeometryDescriptor().getCoordinateReferenceSystem());

        List<Feature> features = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Feature f = super.feature(type, String.valueOf(i), String.format("POINT(%d %d)", i, i),
                    "f-" + i);
            features.add(f);
        }
        super.insert(features);
        super.add();
        super.commit("created feature type with null CRS");

        SimpleFeatureSource source = dataStore.getFeatureSource(typeName);
        assertNull(source.getSchema().getGeometryDescriptor().getCoordinateReferenceSystem());

        Filter bbox;
        SimpleFeatureCollection coll;

        bbox = ff.bbox("the_geom", -0.5, -0.5, 0.5, 0.5, "EPSG:4326");
        coll = source.getFeatures(bbox);
        assertEquals(1, coll.size());

        bbox = ff.bbox("the_geom", -0.5, -0.5, 0.5, 0.5, "EPSG:3857");
        coll = source.getFeatures(bbox);
        assertEquals(1, coll.size());
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

    @Test
    public void testRetype() throws Exception {
        Query query = new Query();
        query.setPropertyNames(new String[] { "sp", "ip", "pp" });
        SimpleFeatureCollection collection = pointsSource.getFeatures(query);

        // no retyping, requested all properties in same order
        assertEquals(pointsSource.getSchema(), collection.getSchema());

        testRetype(pointsSource, "pp", "ip");
        testRetype(pointsSource, "pp");
        testRetype(pointsSource, "sp");
        testRetype(pointsSource, "pp", "sp");
    }

    /**
     * @see GeogigFeatureVisitorHandler
     */
    public @Test void handleMinVisitorMaterializedAttribute() {
        NodeRef typeRef = pointsSource.delegate.getTypeRef();
        createIndex(typeRef, Collections.singletonList("ip"));
        testVisitor(new MinVisitor("ip"), Integer.valueOf(1000));
    }

    public @Test void handleMinVisitorNotIndexed() {
        testVisitor(new MinVisitor("ip"), Integer.valueOf(1000));
    }

    /**
     * @see GeogigFeatureVisitorHandler
     */
    public @Test void handleUniqueVisitorMaterializedAttribute() {
        NodeRef typeRef = pointsSource.delegate.getTypeRef();
        createIndex(typeRef, Collections.singletonList("ip"));
        testVisitor(new UniqueVisitor("ip"), Sets.newHashSet(1000, 2000, 3000));
    }

    public @Test void handleUniqueVisitorNotIndexed() {
        testVisitor(new UniqueVisitor("ip"), Sets.newHashSet(1000, 2000, 3000));
    }

    /**
     * @see GeogigFeatureVisitorHandler
     */
    public @Test void handleMaxVisitorMaterializedAttribute() {
        NodeRef typeRef = pointsSource.delegate.getTypeRef();
        createIndex(typeRef, Collections.singletonList("ip"));
        testVisitor(new MaxVisitor("ip"), Integer.valueOf(3000));
    }

    public @Test void handleMaxVisitorNotIndexed() {
        testVisitor(new MaxVisitor("ip"), Integer.valueOf(3000));
    }

    /**
     * @see GeogigFeatureVisitorHandler
     */
    public @Test void handleNearestVisitorMaterializedAttribute() {
        NodeRef typeRef = pointsSource.delegate.getTypeRef();
        createIndex(typeRef, Collections.singletonList("ip"));
        final int valueToMatch = 2050;
        Integer expectedResult = Integer.valueOf(2000);
        testVisitor(new NearestVisitor(ff.property("ip"), valueToMatch), expectedResult);
    }

    public @Test void handleNearestVisitorNotIndexed() {
        final int valueToMatch = 2050;
        Integer expectedResult = Integer.valueOf(2000);
        testVisitor(new NearestVisitor(ff.property("ip"), valueToMatch), expectedResult);
    }

    private void testVisitor(FeatureCalc visitor, Object expectedResult) {
        GeogigFeatureVisitorHandler.clearCache();
        boolean handled = pointsSource.handleVisitor(Query.ALL, visitor);
        assertTrue(handled);
        CalcResult res = visitor.getResult();
        assertEquals(expectedResult, res.getValue());
        // run again
        handled = pointsSource.handleVisitor(Query.ALL, visitor);
        assertTrue(handled);
        res = visitor.getResult();
        assertEquals(expectedResult, res.getValue());
    }

    private Index createIndex(NodeRef typeRef, List<String> extraAttributes) {
        Index index = repo.command(CreateQuadTree.class)//
                .setBounds(QuadTreeTestSupport.wgs84Bounds())//
                .setTypeTreeRef(typeRef)//
                .setExtraAttributes(extraAttributes)//
                .call();
        return index;
    }

    private void testRetype(SimpleFeatureSource pointsSource, String... properties)
            throws IOException {

        Query query = new Query();
        query.setPropertyNames(properties);

        SimpleFeatureCollection collection = pointsSource.getFeatures(query);

        assertNotEquals(pointsSource.getSchema(), collection.getSchema());
        assertEquals(properties.length, collection.getSchema().getAttributeCount());
        for (int i = 0; i < properties.length; i++) {
            assertEquals(properties[i], collection.getSchema().getDescriptor(i).getLocalName());
        }

        SimpleFeature[] features = collection.toArray(new SimpleFeature[0]);
        for (SimpleFeature f : features) {
            assertEquals(collection.getSchema(), f.getFeatureType());
        }
    }
}
