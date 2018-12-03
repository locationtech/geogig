/* Copyright (c) 2017 Boundless and others.
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.factory.Hints;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.model.internal.QuadTreeTestSupport;
import org.locationtech.geogig.plumbing.index.IndexTestSupport;
import org.locationtech.geogig.porcelain.index.CreateQuadTree;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

/**
 * Run all {@link GeoGigFeatureStoreTest} test cases and make sure the {@link IndexInfo index} is
 * correctly updated
 *
 */
public class GeoGigFeatureStoreIndexedTest extends GeoGigFeatureStoreTest {

    private Index createIndex(NodeRef typeRef, String... extraAttributes) {
        List<String> extraAtts = extraAttributes == null ? null : Arrays.asList(extraAttributes);
        return createIndex(typeRef, extraAtts);
    }

    private Index createIndex(NodeRef typeRef, List<String> extraAttributes) {
        Index index = repo.command(CreateQuadTree.class)//
                .setBounds(QuadTreeTestSupport.wgs84Bounds())//
                .setTypeTreeRef(typeRef)//
                .setExtraAttributes(extraAttributes)//
                .call();
        return index;
    }

    public @Test void testMoveGeometriesFromUnpromotablesToQuad() throws IOException {
        final Index polygonsInitialIndex;
        {
            GeogigFeatureStore polys = (GeogigFeatureStore) dataStore.getFeatureSource(polyName);
            NodeRef typeRef = polys.delegate.getTypeRef();
            polygonsInitialIndex = createIndex(typeRef, "sp", "ip");
            ObjectId indexTreeId = polygonsInitialIndex.indexTreeId();
            ObjectId canonicalTreeId = typeRef.getObjectId();
            IndexTestSupport.verifyIndex(geogig, indexTreeId, canonicalTreeId, "sp", "ip");
        }

        final Geometry unpromotable = geom("POLYGON((-1 -1,-1 1,1 1,1 -1,-1 -1))");
        final Geometry promotable = geom("POLYGON((0.1 0.1,0.1 0.2,0.2 0.2,0.2 0.1,0.1 0.1))");

        final List<SimpleFeature> features;
        features = createClones(130, feature(polyType, "0", "sval", 1, unpromotable));
        {
            SimpleFeatureStore store = (SimpleFeatureStore) dataStore.getFeatureSource(polyName);
            Transaction tx = new DefaultTransaction();
            store.setTransaction(tx);
            store.addFeatures(DataUtilities.collection(features));
            tx.commit();
            tx.close();
        }
        verify(polygonsInitialIndex.info(), features);

        final List<SimpleFeature> expectedFeatures;
        expectedFeatures = createClones(130, feature(polyType, "0", "sval", 1, promotable));
        {
            SimpleFeatureStore store = (SimpleFeatureStore) dataStore.getFeatureSource(polyName);
            Transaction tx = new DefaultTransaction();
            store.setTransaction(tx);
            store.modifyFeatures("pp", promotable, Filter.INCLUDE);
            tx.commit();
            tx.close();
        }
        verify(polygonsInitialIndex.info(), expectedFeatures);
    }

    public @Test void testVerySmallLineStrings() throws Exception {
        Geometry smallGeom = geom(
                "LINESTRING(1.401298464324817E-45 1.401298464324817E-45,2.802596928649634E-45 2.802596928649634E-45)");
        Geometry updateGeom = geom("LINESTRING(1 1, 1.000001 1.000001)");

        testVerySmallGeometries(linesType, 130, smallGeom, updateGeom);
    }

    private void testVerySmallGeometries(SimpleFeatureType type, final int count,
            final Geometry initialGeom, final Geometry updateGeom, final String... extraAttributes)
            throws Exception {
        final String typeName = type.getTypeName();
        final Index index;
        {
            GeogigFeatureStore store = (GeogigFeatureStore) dataStore.getFeatureSource(typeName);
            NodeRef typeRef = store.delegate.getTypeRef();
            index = createIndex(typeRef);
        }
        final String geometryAttribute = type.getGeometryDescriptor().getLocalName();

        final List<SimpleFeature> features;
        features = createClones(count, feature(type, "0", "sval", 1, initialGeom));
        {
            SimpleFeatureStore store = (SimpleFeatureStore) dataStore.getFeatureSource(typeName);
            Transaction tx = new DefaultTransaction();
            store.setTransaction(tx);
            store.addFeatures(DataUtilities.collection(features));

            assertEquals(count, store.getCount(Query.ALL));
            tx.commit();
            tx.close();
            store = (SimpleFeatureStore) dataStore.getFeatureSource(typeName);
            assertEquals(count, store.getCount(Query.ALL));
        }
        verify(index.info(), features);

        final List<SimpleFeature> expectedFeatures;
        expectedFeatures = createClones(count, feature(type, "1", "sval-updated", 2, updateGeom));
        {
            SimpleFeatureStore store = (SimpleFeatureStore) dataStore.getFeatureSource(typeName);
            Transaction tx;
            tx = new DefaultTransaction();
            store.setTransaction(tx);
            store.modifyFeatures(geometryAttribute, updateGeom, Filter.INCLUDE);
            tx.commit();
            tx.close();

            tx = new DefaultTransaction();
            store.setTransaction(tx);
            store.modifyFeatures(type.getDescriptor("ip").getName(), 2, Filter.INCLUDE);
            tx.commit();
            tx.close();

            tx = new DefaultTransaction();
            store.setTransaction(tx);
            store.modifyFeatures(type.getDescriptor("sp").getName(), "sval-updated",
                    Filter.INCLUDE);
            tx.commit();

            tx.close();
            store = (SimpleFeatureStore) dataStore.getFeatureSource(typeName);
            assertEquals(count, store.getCount(Query.ALL));
        }
        verify(index.info(), expectedFeatures);
    }

    private void verify(IndexInfo indexInfo, List<SimpleFeature> expectedFeatures)
            throws IOException {
        String typeName = indexInfo.getTreeName();
        GeogigFeatureStore source = (GeogigFeatureStore) dataStore.getFeatureSource(typeName);
        assertEquals(expectedFeatures.size(), source.getCount(Query.ALL));
        NodeRef typeRef = source.delegate.getTypeRef();
        ObjectId canonicalTreeId = typeRef.getObjectId();
        Optional<ObjectId> resolveIndexedTree = repo.indexDatabase().resolveIndexedTree(indexInfo,
                canonicalTreeId);
        assertTrue(resolveIndexedTree.isPresent());
        ObjectId indexTreeId = resolveIndexedTree.get();

        Set<String> extraAttributes = IndexInfo.getMaterializedAttributeNames(indexInfo);
        String[] extraAtts = new ArrayList<>(extraAttributes).toArray(new String[0]);
        IndexTestSupport.verifyIndex(geogig, indexTreeId, canonicalTreeId, extraAtts);

        List<SimpleFeature> contents = DataUtilities.list(source.getFeatures());

        Map<String, SimpleFeature> expected = Maps.uniqueIndex(expectedFeatures, (f) -> f.getID());
        Map<String, SimpleFeature> actual = Maps.uniqueIndex(contents, (f) -> f.getID());
        assertEquals(expected.size(), actual.size());
        assertEquals(expected.keySet(), actual.keySet());
        for (String fid : expected.keySet()) {
            SimpleFeature e = expected.get(fid);
            SimpleFeature a = actual.get(fid);
            List<Object> eatts = e.getAttributes();
            List<Object> aatts = a.getAttributes();
            assertEquals(eatts, aatts);
        }
    }

    private List<SimpleFeature> createClones(int count, SimpleFeature proto) {
        List<SimpleFeature> list = new ArrayList<>(count);
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(proto.getFeatureType());
        for (int i = 0; i < count; i++) {
            builder.init(proto);
            String fid = String.valueOf(i);
            SimpleFeature f = builder.buildFeature(fid);
            f.getUserData().put(Hints.USE_PROVIDED_FID, Boolean.TRUE);
            f.getUserData().put(Hints.PROVIDED_FID, fid);
            list.add(f);
        }
        return list;
    }

    @Test
    public void testRemoveFeatures2() throws Exception {
        final NodeRef layerNode = IndexTestSupport.createWorldPointsLayer(repo);
        final String typeName = layerNode.getNode().getName();

        add();
        commit("created layer " + typeName);
        final Index initialIndex = createIndex(layerNode);

        final Filter filter = ff.bbox("geom", 0, 0, 180, 90, "EPSG:4326");

        GeogigFeatureStore store = (GeogigFeatureStore) dataStore.getFeatureSource(typeName);

        final int total = store.getCount(Query.ALL);
        final int matching = store.getCount(new Query(typeName, filter));
        assertTrue(matching > 0);
        final int expected = total - matching;

        List<SimpleFeature> expectedResult = DataUtilities.list(store.getFeatures());

        for (Iterator<SimpleFeature> it = expectedResult.iterator(); it.hasNext();) {
            SimpleFeature f = it.next();
            if (filter.evaluate(f)) {
                it.remove();
            }
        }

        Transaction tx = new DefaultTransaction();
        store.setTransaction(tx);
        try {
            // initial # of features
            assertEquals(total, store.getCount(Query.ALL));
            // remove feature
            store.removeFeatures(filter);

            // #of features before commit on the same store
            assertEquals(expected, store.getCount(Query.ALL));

            // #of features before commit on a different store instance
            assertEquals(total, dataStore.getFeatureSource(typeName).getCount(Query.ALL));

            tx.commit();

            // #of features after commit on a different store instance
            assertEquals(expected, dataStore.getFeatureSource(typeName).getCount(Query.ALL));
        } catch (Exception e) {
            tx.rollback();
            throw e;
        } finally {
            tx.close();
        }
        store.setTransaction(Transaction.AUTO_COMMIT);
        assertEquals(expected, store.getFeatures().size());
        assertEquals(0, store.getFeatures(filter).size());

        verify(initialIndex.info(), expectedResult);
    }

    @Test
    public void testRemoveFeaturesUnpromotables() throws Exception {
        final NodeRef layerNode = IndexTestSupport.createWorldPointsLayer(repo);
        final String typeName = layerNode.getNode().getName();

        add();
        commit("created layer " + typeName);
        createIndex(layerNode);

        final Filter filter = ff.bbox("pp", 0, 0, 180, 90, "EPSG:4326");

        GeogigFeatureStore store = (GeogigFeatureStore) dataStore.getFeatureSource(typeName);

        final int total = store.getCount(Query.ALL);
        final int matching = store.getCount(new Query(typeName, filter));
        assertTrue(matching > 0);
        final int expected = total - matching;

        Transaction tx = new DefaultTransaction();
        store.setTransaction(tx);
        try {
            // initial # of features
            assertEquals(total, store.getCount(Query.ALL));
            // remove feature
            store.removeFeatures(filter);

            // #of features before commit on the same store
            assertEquals(expected, store.getCount(Query.ALL));

            // #of features before commit on a different store instance
            assertEquals(total, dataStore.getFeatureSource(typeName).getCount(Query.ALL));

            tx.commit();

            // #of features after commit on a different store instance
            assertEquals(expected, dataStore.getFeatureSource(typeName).getCount(Query.ALL));
        } catch (Exception e) {
            tx.rollback();
            throw e;
        } finally {
            tx.close();
        }
        store.setTransaction(Transaction.AUTO_COMMIT);
        assertEquals(expected, store.getFeatures().size());
        assertEquals(0, store.getFeatures(filter).size());

        // ObjectId indexTreeId;
        // ObjectId canonicalTreeId;
        // String extraAttributes;
        // IndexTestSupport.verifyIndex(geogig, indexTreeId, canonicalTreeId, extraAttributes);
    }

    public static NodeRef createOddLayer(String typeName, Repository repository) {
        String typeSpec = "geom:Point:srid=4326,x:Double,y:Double,xystr:String";
        SimpleFeatureType type;
        try {
            type = DataUtilities.createType(typeName, typeSpec);
        } catch (SchemaException e) {
            throw new RuntimeException(e);
        }
        RevTree tree = IndexTestSupport.createWorldPointsTree(repository);
        WorkingTree workingTree = repository.workingTree();
        NodeRef typeTreeRef = workingTree.createTypeTree(type.getTypeName(), type);

        ObjectStore store = repository.objectDatabase();
        CanonicalTreeBuilder newRootBuilder = CanonicalTreeBuilder.create(store,
                workingTree.getTree());

        NodeRef newTypeTreeRef = typeTreeRef.update(tree.getId(), SpatialOps.boundsOf(tree));
        newRootBuilder.put(newTypeTreeRef.getNode());
        RevTree newWorkTree = newRootBuilder.build();
        workingTree.updateWorkHead(newWorkTree.getId());
        return newTypeTreeRef;
    }

    public static RevTree createTree_AllFeaturesSameGeometry(int numFeatues,
            Repository repository) {

        double x = 1.000000001;
        double y = 2.000000002;

        ObjectStore store = repository.objectDatabase();
        CanonicalTreeBuilder builder = CanonicalTreeBuilder.create(store);
        for (int i = 0; i < numFeatues; i++) {
            String fid = String.valueOf(i);

            RevFeature feature;
            feature = IndexTestSupport.createPointFeature(x, y, Double.valueOf(i),
                    Double.valueOf(i), fid);
            Envelope env = SpatialOps.boundsOf(feature);

            ObjectId oid = feature.getId();
            Node node = RevObjectFactory.defaultInstance().createNode(fid, oid, ObjectId.NULL,
                    TYPE.FEATURE, env, null);
            store.put(feature);
            builder.put(node);
        }
        RevTree tree = builder.build();
        return tree;
    }
}
