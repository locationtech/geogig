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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureSource;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.NameImpl;
import org.geotools.geometry.jts.GeometryBuilder;
import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;

import com.google.common.base.Optional;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

public class GeoGigDataStoreTest extends RepositoryTestCase {

    private GeoGigDataStore dataStore;

    @Override
    protected void setUpInternal() throws Exception {
        dataStore = new GeoGigDataStore(geogig.getRepository());
    }

    @Override
    protected void tearDownInternal() throws Exception {
        dataStore.dispose();
        dataStore = null;
    }

    @Test
    public void testDispose() {
        assertTrue(geogig.isOpen());
        dataStore.dispose();
        assertFalse(geogig.isOpen());
    }

    private List<String> getTypeNames(String head) {

        Iterator<NodeRef> typeTrees = geogig.command(LsTreeOp.class)
                .setStrategy(Strategy.TREES_ONLY).setReference(head).call();

        List<String> typeNames = Lists
                .newArrayList(Iterators.transform(typeTrees, (ref) -> ref.name()));
        return typeNames;
    }

    @Test
    public void testCreateSchema() throws IOException {
        final SimpleFeatureType featureType = super.linesType;
        dataStore.createSchema(featureType);

        List<String> typeNames;
        typeNames = getTypeNames(Ref.HEAD);
        assertEquals(1, typeNames.size());
        assertEquals(linesName, typeNames.get(0));

        dataStore.createSchema(super.pointsType);

        typeNames = getTypeNames(Ref.HEAD);
        assertEquals(2, typeNames.size());
        assertTrue(typeNames.contains(linesName));
        assertTrue(typeNames.contains(pointsName));

        try {
            dataStore.createSchema(super.pointsType);
            fail("Expected IOException on existing type");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("already exists"));
        }
    }

    @Test
    public void testCreateSchemaOnBranch() throws IOException {
        final String branchName = "testBranch";
        geogig.command(BranchCreateOp.class).setName(branchName).setOrphan(true).call();

        dataStore.setHead(branchName);
        final SimpleFeatureType featureType = super.linesType;
        dataStore.createSchema(featureType);

        List<String> typeNames;
        typeNames = getTypeNames(Ref.HEAD);
        assertTrue(typeNames.isEmpty());

        typeNames = getTypeNames(branchName);
        assertEquals(1, typeNames.size());
        assertEquals(linesName, typeNames.get(0));

        dataStore.createSchema(super.pointsType);

        typeNames = getTypeNames(Ref.HEAD);
        assertTrue(typeNames.isEmpty());

        typeNames = getTypeNames(branchName);
        assertEquals(2, typeNames.size());
        assertTrue(typeNames.contains(linesName));
        assertTrue(typeNames.contains(pointsName));
    }

    @Test
    public void testGetNames() throws Exception {

        assertEquals(0, dataStore.getNames().size());

        insertAndAdd(points1);
        assertEquals(0, dataStore.getNames().size());
        commit();

        assertEquals(1, dataStore.getNames().size());

        insertAndAdd(lines1);
        assertEquals(1, dataStore.getNames().size());
        commit();

        assertEquals(2, dataStore.getNames().size());

        List<Name> names = dataStore.getNames();
        // ContentDataStore doesn't support native namespaces
        // assertTrue(names.contains(RepositoryTestCase.linesTypeName));
        // assertTrue(names.contains(RepositoryTestCase.pointsTypeName));
        assertTrue(names.contains(new NameImpl(pointsName)));
        assertTrue(names.contains(new NameImpl(linesName)));
    }

    @Test
    public void testGetTypeNames() throws Exception {

        assertEquals(0, dataStore.getTypeNames().length);

        insertAndAdd(lines1);
        assertEquals(0, dataStore.getTypeNames().length);
        commit();

        assertEquals(1, dataStore.getTypeNames().length);

        insertAndAdd(points1);
        assertEquals(1, dataStore.getTypeNames().length);
        commit();

        assertEquals(2, dataStore.getTypeNames().length);

        List<String> simpleNames = Arrays.asList(dataStore.getTypeNames());

        assertTrue(simpleNames.contains(linesName));
        assertTrue(simpleNames.contains(pointsName));
    }

    @Test
    public void testGetSchemaName() throws Exception {
        try {
            dataStore.getSchema(RepositoryTestCase.linesTypeName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("does not exist"));
        }

        insertAndAdd(lines1);
        try {
            dataStore.getSchema(RepositoryTestCase.linesTypeName);
            fail("Expected IOE as type hasn't been committed");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("does not exist"));
        }
        commit();
        SimpleFeatureType lines = dataStore.getSchema(RepositoryTestCase.linesTypeName);
        assertEquals(super.linesType, lines);

        try {
            dataStore.getSchema(RepositoryTestCase.pointsTypeName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        insertAndAdd(points1);
        commit();
        SimpleFeatureType points = dataStore.getSchema(RepositoryTestCase.pointsTypeName);
        assertEquals(super.pointsType, points);
    }

    private ObjectId commit() {
        org.locationtech.geogig.model.RevCommit c = geogig.command(CommitOp.class).call();
        return c.getId();
    }

    @Test
    public void testGetSchemaProvidedNamespace() throws Exception {
        String namespace = "http://www.geogig.org/test";
        dataStore.setNamespaceURI(namespace);
        insertAndAdd(lines1);
        commit();
        SimpleFeatureType lines = dataStore.getSchema(RepositoryTestCase.linesTypeName);
        Name expectedName = new NameImpl(namespace, linesName);
        assertEquals(expectedName, lines.getName());
        assertEquals(super.linesType.getAttributeDescriptors(), lines.getAttributeDescriptors());

        insertAndAdd(points1);
        commit();
        SimpleFeatureType points = dataStore.getSchema(RepositoryTestCase.pointsTypeName);
        assertEquals(new NameImpl(namespace, pointsName), points.getName());
        assertEquals(super.pointsType.getAttributeDescriptors(), points.getAttributeDescriptors());
    }

    @Test
    public void testGetFeatureSourceProvidedNamespace() throws Exception {
        String namespace = "http://www.geogig.org/test";
        dataStore.setNamespaceURI(namespace);
        insertAndAdd(lines1);
        commit();
        SimpleFeatureSource lines;
        lines = dataStore.getFeatureSource(linesName);
        Name expectedName = new NameImpl(namespace, linesName);

        assertEquals(expectedName, lines.getName());
        SimpleFeatureType schema = lines.getSchema();
        assertEquals(expectedName, schema.getName());

        SimpleFeatureCollection features = lines.getFeatures();
        assertEquals(expectedName, features.getSchema().getName());
        try (SimpleFeatureIterator it = features.features()) {
            SimpleFeature feature = it.next();
            assertEquals(expectedName, feature.getType().getName());
        }
    }

    @Test
    public void testGetSchemaString() throws Exception {
        try {
            dataStore.getSchema(RepositoryTestCase.linesName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        insertAndAdd(lines1);
        commit();
        SimpleFeatureType lines = dataStore.getSchema(RepositoryTestCase.linesName);
        assertEquals(super.linesType.getAttributeDescriptors(), lines.getAttributeDescriptors());

        try {
            dataStore.getSchema(RepositoryTestCase.pointsName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        insertAndAdd(points1);
        commit();
        SimpleFeatureType points = dataStore.getSchema(RepositoryTestCase.pointsName);
        assertEquals(super.pointsType, points);
    }

    @Test
    public void testGetFeatureSourceName() throws Exception {
        try {
            dataStore.getFeatureSource(RepositoryTestCase.linesName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        SimpleFeatureSource source;

        insertAndAdd(lines1);
        try {
            dataStore.getFeatureSource(RepositoryTestCase.linesName);
            fail("Expected IOE as feature typ is not committed yet");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("does not exist"));
        }
        commit();
        source = dataStore.getFeatureSource(RepositoryTestCase.linesName);
        assertTrue(source instanceof GeogigFeatureStore);

        try {
            dataStore.getFeatureSource(RepositoryTestCase.pointsName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        insertAndAdd(points1);
        commit();
        source = dataStore.getFeatureSource(RepositoryTestCase.pointsName);
        assertTrue(source instanceof GeogigFeatureStore);
    }

    @Test
    public void testGetFeatureSourceString() throws Exception {
        try {
            dataStore.getFeatureSource(RepositoryTestCase.linesName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        SimpleFeatureSource source;

        insertAndAdd(lines1);
        commit();
        source = dataStore.getFeatureSource(RepositoryTestCase.linesName);
        assertTrue(source instanceof GeogigFeatureStore);

        try {
            dataStore.getFeatureSource(RepositoryTestCase.pointsName);
            fail("Expected IOException");
        } catch (IOException e) {
            assertTrue(true);
        }

        insertAndAdd(points1);
        commit();
        source = dataStore.getFeatureSource(RepositoryTestCase.pointsName);
        assertTrue(source instanceof GeogigFeatureStore);
    }

    @Test
    public void testFeatureWriterAppend() throws Exception {
        dataStore.createSchema(linesType);

        Transaction tx = new DefaultTransaction();
        FeatureWriter<SimpleFeatureType, SimpleFeature> fw = dataStore
                .getFeatureWriterAppend(linesTypeName.getLocalPart(), tx);

        LineString line = new GeometryBuilder().lineString(0, 0, 1, 1);
        SimpleFeature f = (SimpleFeature) fw.next();
        f.setAttribute("sp", "foo");
        f.setAttribute("ip", 10);
        f.setAttribute("pp", line);

        fw.write();
        fw.close();

        tx.commit();
        tx.close();

        FeatureSource<SimpleFeatureType, SimpleFeature> source = dataStore
                .getFeatureSource(linesName);
        assertEquals(1, source.getCount(null));

        FeatureReader<SimpleFeatureType, SimpleFeature> r = dataStore
                .getFeatureReader(new Query(linesName), Transaction.AUTO_COMMIT);
        assertTrue(r.hasNext());

        f = r.next();
        assertEquals("foo", f.getAttribute("sp"));
        assertEquals(10, f.getAttribute("ip"));
        assertTrue(line.equals((Geometry) f.getAttribute("pp")));
    }

    public @Test void testFindTypeRef() throws Exception {
        Name typeName = new NameImpl(pointsName);
        final Transaction autoCommit = Transaction.AUTO_COMMIT;
        final Transaction tx = new DefaultTransaction();
        try {
            dataStore.findTypeRef(typeName, autoCommit);
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
        dataStore.createSchema(pointsType);

        final NodeRef firstRef = dataStore.findTypeRef(typeName, autoCommit);
        assertNotNull(firstRef);
        assertEquals(RevTree.EMPTY_TREE_ID, firstRef.getObjectId());

        insertAndAdd(points1);
        commit("commit outside datastore");

        final NodeRef secondRef = dataStore.findTypeRef(typeName, autoCommit);
        assertNotNull(secondRef);
        assertNotEquals(RevTree.EMPTY_TREE_ID, secondRef.getObjectId());

        SimpleFeatureStore store = (SimpleFeatureStore) dataStore.getFeatureSource(typeName);
        store.setTransaction(tx);
        store.addFeatures(DataUtilities.collection((SimpleFeature) points2));

        assertEquals(secondRef, dataStore.findTypeRef(typeName, autoCommit));
        final NodeRef txRef = dataStore.findTypeRef(typeName, tx);
        assertNotNull(txRef);
        assertNotEquals(secondRef, txRef);

        tx.commit();
        tx.close();

        assertEquals(txRef, dataStore.findTypeRef(typeName, autoCommit));
    }

    private Optional<IndexInfo> createOrUpdateIndexAndVerify(String layerName,
            String... extraAttributes) throws Exception {
        Optional<ObjectId> createOrUpdateIndex = dataStore.createOrUpdateIndex(layerName,
                extraAttributes);
        assertTrue("IndexInfo ObjectId should be present", createOrUpdateIndex.isPresent());
        ObjectId id = createOrUpdateIndex.get();
        Context resolveContext = dataStore.resolveContext(Transaction.AUTO_COMMIT);
        List<IndexInfo> indexInfos = resolveContext.indexDatabase().getIndexInfos();
        assertNotNull("No IndexInfo objects found", indexInfos);
        assertEquals("Expected exactly 1 IndexInfo", 1, indexInfos.size());
        IndexInfo index = indexInfos.get(0);
        assertEquals("Unexpected ObjectID for IndexInfo", id, index.getId());
        // verify the index contains all the extra Attributes
        Set<String> materializedAttributeNames = IndexInfo.getMaterializedAttributeNames(index);
        for (String attribute : extraAttributes) {
            assertTrue("Index should have contained " + attribute,
                    materializedAttributeNames.contains(attribute));
        }
        return Optional.of(index);
    }

    private void verifyExtraAttributes(IndexInfo index, String... extraAttributes)
            throws Exception {
        Set<String> materializedAttributeNames = IndexInfo.getMaterializedAttributeNames(index);
        if (extraAttributes.length > 0) {
            assertFalse("There should be extra attributes for the Index",
                    materializedAttributeNames.isEmpty());
            for (String attr : extraAttributes) {
                assertTrue("Attribute \"" + attr + "\" should be present in the Index",
                        materializedAttributeNames.contains(attr));
            }
        }
    }

    @Test
    public void testCreateOrUpdateIndex() throws Exception {
        insertAndAdd(lines1);
        commit();
        IndexInfo createOrUpdateIndex = createOrUpdateIndexAndVerify(linesName, new String[] {})
                .get();
        ObjectId id = createOrUpdateIndex.getId();
        // Index should not have any extra attributes
        Set<String> materializedAttributeNames = IndexInfo
                .getMaterializedAttributeNames(createOrUpdateIndex);
        assertTrue("There should be no extra attributes for the Index",
                materializedAttributeNames.isEmpty());

        // now update the Index with a non-geometry attribute (sp)
        createOrUpdateIndex = createOrUpdateIndexAndVerify(linesName, new String[] { "sp" }).get();
        // id should match the one we got when the Index was created above
        ObjectId secondId = createOrUpdateIndex.getId();
        assertEquals("Index does not match", id, secondId);
        // Index should have a single "sp" extra attribute
        verifyExtraAttributes(createOrUpdateIndex, new String[] { "sp" });

        // now update the Index with the same "sp" attribute
        createOrUpdateIndex = createOrUpdateIndexAndVerify(linesName, new String[] { "sp" }).get();
        // id should match the one we got when the Index was created above
        ObjectId thirdId = createOrUpdateIndex.getId();
        assertEquals("Index does not match", id, thirdId);
        // Index should have a single "sp" extra attribute
        verifyExtraAttributes(createOrUpdateIndex, new String[] { "sp" });

        // update the Index with two attributes, "sp" and "ip"
        createOrUpdateIndex = createOrUpdateIndexAndVerify(linesName, new String[] { "sp", "ip" })
                .get();
        // id should match the one we got when the Index was created above
        ObjectId fourthId = createOrUpdateIndex.getId();
        assertEquals("Index does not match", id, fourthId);
        // Index should have a single "sp" extra attribute
        verifyExtraAttributes(createOrUpdateIndex, new String[] { "ip", "sp" });

        // now make sure a call with 1 attribute doesn't clear existing extra attributes
        createOrUpdateIndex = createOrUpdateIndexAndVerify(linesName, new String[] { "ip" }).get();
        // id should match the one we got when the Index was created above
        ObjectId fifthId = createOrUpdateIndex.getId();
        assertEquals("Index does not match", id, fifthId);
        // Index should have a single "sp" extra attribute
        verifyExtraAttributes(createOrUpdateIndex, new String[] { "sp", "ip" });

        // lastly, update the index with no extra attributes and make sure there are still 2
        createOrUpdateIndex = createOrUpdateIndexAndVerify(linesName, new String[] {}).get();
        // id should match the one we got when the Index was created above
        ObjectId sixthId = createOrUpdateIndex.getId();
        assertEquals("Index does not match", id, sixthId);
        // Index should have a single "sp" extra attribute
        verifyExtraAttributes(createOrUpdateIndex, new String[] { "ip", "sp" });
    }
}
