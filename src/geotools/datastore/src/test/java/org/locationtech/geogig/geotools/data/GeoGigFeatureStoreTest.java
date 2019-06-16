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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.FeatureCollection;
import org.geotools.util.factory.Hints;
import org.hamcrest.core.IsInstanceOf;
import org.hamcrest.core.StringContains;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.geotools.adapt.GT;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.Feature;
import org.opengis.feature.IllegalAttributeException;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.Id;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.identity.ResourceId;

public class GeoGigFeatureStoreTest extends RepositoryTestCase {

    protected static final FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2(null);

    @Rule
    public ExpectedException expected = ExpectedException.none();

    protected GeoGigDataStore dataStore;

    protected GeogigFeatureStore points;

    @Override
    protected void setUpInternal() throws Exception {
        dataStore = new GeoGigDataStore(repo);
        dataStore.createSchema(GT.adapt(super.pointsType));
        dataStore.createSchema(GT.adapt(super.linesType));
        dataStore.createSchema(GT.adapt(super.polyType));

        points = (GeogigFeatureStore) dataStore.getFeatureSource(pointsName);
    }

    @Override
    protected void tearDownInternal() throws Exception {
        dataStore.dispose();
        dataStore = null;
        points = null;
    }

    private SimpleFeatureCollection collection(
            org.locationtech.geogig.feature.Feature... gigFeatures) {
        List<SimpleFeature> simpleFeatures = Arrays.asList(gigFeatures).stream().map(GT::adapt)
                .collect(Collectors.toList());
        SimpleFeatureCollection collection = DataUtilities.collection(simpleFeatures);
        return collection;
    }

    @Test
    public void testAddFeatures() throws Exception {
        SimpleFeatureCollection collection = collection(points1, points2, points3);

        Transaction tx = new DefaultTransaction();
        points.setTransaction(tx);
        assertSame(tx, points.getTransaction());
        try {
            List<FeatureId> addedFeatures = points.addFeatures(collection);
            assertNotNull(addedFeatures);
            assertEquals(3, addedFeatures.size());

            for (FeatureId id : addedFeatures) {
                assertFalse(id instanceof ResourceId);
                assertNotNull(id.getFeatureVersion());
            }

            // assert transaction isolation

            assertEquals(3, points.getFeatures().size());
            assertEquals(0, dataStore.getFeatureSource(pointsName).getFeatures().size());

            tx.commit();

            assertEquals(3, dataStore.getFeatureSource(pointsName).getFeatures().size());
        } catch (Exception e) {
            tx.rollback();
            throw e;
        } finally {
            tx.close();
        }
    }

    @Test
    public void testAddFeaturesOnASeparateBranch() throws Exception {
        final String branchName = "addtest";
        final Ref branchRef = repo.command(BranchCreateOp.class).setName(branchName).call();
        dataStore.setHead(branchName);

        FeatureCollection<SimpleFeatureType, SimpleFeature> collection;
        collection = DataUtilities.collection(Arrays.asList((SimpleFeature) points1,
                (SimpleFeature) points2, (SimpleFeature) points3));

        Transaction tx = new DefaultTransaction();
        points.setTransaction(tx);
        assertSame(tx, points.getTransaction());
        try {
            List<FeatureId> addedFeatures = points.addFeatures(collection);
            assertNotNull(addedFeatures);
            assertEquals(3, addedFeatures.size());
            // assert transaction isolation
            assertEquals(3, points.getFeatures().size());
            assertEquals(0, dataStore.getFeatureSource(pointsName).getFeatures().size());

            tx.commit();

            assertEquals(3, dataStore.getFeatureSource(pointsName).getFeatures().size());
        } catch (Exception e) {
            tx.rollback();
            throw e;
        } finally {
            tx.close();
        }
    }

    @Test
    public void testAddFeaturesWhileNotOnABranch() throws Exception {
        boolean gotIllegalStateException = false;
        final ObjectId head = repo.command(RevParse.class).setRefSpec("HEAD").call().get();
        dataStore.setHead(head.toString());

        FeatureCollection<SimpleFeatureType, SimpleFeature> collection;
        collection = DataUtilities.collection(Arrays.asList((SimpleFeature) points1,
                (SimpleFeature) points2, (SimpleFeature) points3));

        Transaction tx = new DefaultTransaction();
        points.setTransaction(tx);
        assertSame(tx, points.getTransaction());
        try {
            List<FeatureId> addedFeatures = points.addFeatures(collection);
            assertNotNull(addedFeatures);
            assertEquals(3, addedFeatures.size());
            // assert transaction isolation
            assertEquals(3, points.getFeatures().size());
            assertEquals(0, dataStore.getFeatureSource(pointsName).getFeatures().size());

            tx.commit();

            assertEquals(3, dataStore.getFeatureSource(pointsName).getFeatures().size());
        } catch (IllegalStateException e) {
            tx.rollback();
            gotIllegalStateException = true;
        } catch (Exception e) {
            tx.rollback();
            throw e;
        } finally {
            tx.close();
        }

        assertTrue(
                "Should throw IllegalStateException when trying to modify data in geogig datastore when it is not configured with a branch.",
                gotIllegalStateException);
    }

    @Test
    public void testUseProvidedFIDSupported() throws Exception {

        assertTrue(points.getQueryCapabilities().isUseProvidedFIDSupported());

        SimpleFeatureType gtPointsType = GT.adapt(pointsType);
        SimpleFeature gtPoints1 = GT.adapt(gtPointsType, points1);
        SimpleFeature gtPoints2 = GT.adapt(gtPointsType, points2);
        SimpleFeature gtPoints3 = GT.adapt(gtPointsType, points3);

        FeatureCollection<SimpleFeatureType, SimpleFeature> collection;
        collection = DataUtilities.collection(Arrays.asList(gtPoints1, gtPoints2, gtPoints3));

        Transaction tx = new DefaultTransaction();
        points.setTransaction(tx);
        try {
            List<FeatureId> newFids = points.addFeatures(collection);
            assertNotNull(newFids);
            assertEquals(3, newFids.size());

            FeatureId fid1 = newFids.get(0);
            FeatureId fid2 = newFids.get(1);
            FeatureId fid3 = newFids.get(2);

            // new ids should have been generated...
            assertFalse(idP1.equals(fid1.getID()));
            assertFalse(idP1.equals(fid1.getID()));
            assertFalse(idP1.equals(fid1.getID()));

            // now force the use of provided feature ids
            gtPoints1.getUserData().put(Hints.USE_PROVIDED_FID, Boolean.TRUE);
            gtPoints2.getUserData().put(Hints.USE_PROVIDED_FID, Boolean.TRUE);
            gtPoints3.getUserData().put(Hints.USE_PROVIDED_FID, Boolean.TRUE);

            List<FeatureId> providedFids = points.addFeatures(collection);
            assertNotNull(providedFids);
            assertEquals(3, providedFids.size());

            FeatureId fid11 = providedFids.get(0);
            FeatureId fid21 = providedFids.get(1);
            FeatureId fid31 = providedFids.get(2);

            // ids should match provided
            assertEquals(idP1, fid11.getID());
            assertEquals(idP2, fid21.getID());
            assertEquals(idP3, fid31.getID());

            tx.commit();

            assertEquals(1, points.getFeatures(ff.id(Collections.singleton(fid1))).size());
            assertEquals(1, points.getFeatures(ff.id(Collections.singleton(fid2))).size());
            assertEquals(1, points.getFeatures(ff.id(Collections.singleton(fid3))).size());

            assertEquals(1, points.getFeatures(ff.id(Collections.singleton(fid11))).size());
            assertEquals(1, points.getFeatures(ff.id(Collections.singleton(fid21))).size());
            assertEquals(1, points.getFeatures(ff.id(Collections.singleton(fid31))).size());

        } catch (Exception e) {
            tx.rollback();
            throw e;
        } finally {
            tx.close();
        }
    }

    @Test
    public void testModifyFeatures() throws Exception {
        // add features circumventing FeatureStore.addFeatures to keep the test
        // independent of the addFeatures functionality
        insertAndAdd(lines1, lines2, lines3, points1, points2, points3);
        repo.command(CommitOp.class).call();

        Id filter = ff.id(Collections.singleton(ff.featureId(idP1)));
        Transaction tx = new DefaultTransaction();
        points.setTransaction(tx);
        try {
            // initial value
            SimpleFeature initial = points.getFeatures(filter).features().next();
            assertEquals("StringProp1_1", initial.getAttribute("sp"));

            // modify
            points.modifyFeatures("sp", "modified", filter);

            // modified value before commit
            SimpleFeature modified = points.getFeatures(filter).features().next();
            assertEquals("modified", modified.getAttribute("sp"));

            // unmodified value before commit on another store instance (tx isolation)
            assertEquals("StringProp1_1", dataStore.getFeatureSource(pointsName).getFeatures(filter)
                    .features().next().getAttribute("sp"));

            tx.commit();

            // modified value after commit on another store instance
            assertEquals("modified", dataStore.getFeatureSource(pointsName).getFeatures(filter)
                    .features().next().getAttribute("sp"));
        } catch (Exception e) {
            tx.rollback();
            throw e;
        } finally {
            tx.close();
        }
        points.setTransaction(Transaction.AUTO_COMMIT);
        SimpleFeature modified = points.getFeatures(filter).features().next();
        assertEquals("modified", modified.getAttribute("sp"));
    }

    @Test
    public void testModifyFeaturesIncompatibleGeometryType() throws Exception {
        insertAndAdd(points1, points2, points3);
        repo.command(CommitOp.class).call();

        Id filter = ff.id(Collections.singleton(ff.featureId(idP1)));

        expected.expect(IOException.class);
        expected.expectMessage("is not assignable to");
        points.modifyFeatures("pp", "LINESTRING(1 1, 2 2)", filter);
    }

    @Test
    public void testModifyFeaturesIncompatibleValueType() throws Exception {
        insertAndAdd(points1, points2, points3);
        repo.command(CommitOp.class).call();

        Id filter = ff.id(Collections.singleton(ff.featureId(idP1)));

        try {
            points.modifyFeatures("pp", "1200", filter);
            fail("Expected IOException");
        } catch (IOException e) {
            assertThat(e.getMessage(), StringContains.containsString("Unable to convert"));
            assertThat(e.getCause(), IsInstanceOf.instanceOf(IllegalAttributeException.class));
        }
    }

    @Test
    public void testRemoveFeatures() throws Exception {
        // add features circumventing FeatureStore.addFeatures to keep the test
        // independent of the addFeatures functionality
        insertAndAdd(lines1, lines2, lines3);
        insertAndAdd(points1, points2, points3);
        repo.command(CommitOp.class).call();

        Id filter = ff.id(Collections.singleton(ff.featureId(idP1)));
        Transaction tx = new DefaultTransaction();
        points.setTransaction(tx);
        try {
            // initial # of features
            assertEquals(3, points.getFeatures().size());
            // remove feature
            points.removeFeatures(filter);

            // #of features before commit on the same store
            assertEquals(2, points.getFeatures().size());

            // #of features before commit on a different store instance
            assertEquals(3, dataStore.getFeatureSource(pointsName).getFeatures().size());

            tx.commit();

            // #of features after commit on a different store instance
            assertEquals(2, dataStore.getFeatureSource(pointsName).getFeatures().size());
        } catch (Exception e) {
            tx.rollback();
            throw e;
        } finally {
            tx.close();
        }
        points.setTransaction(Transaction.AUTO_COMMIT);
        assertEquals(2, points.getFeatures().size());
        assertEquals(0, points.getFeatures(filter).size());
    }

    @Test
    public void testTransactionCommitMessage() throws Exception {

        FeatureCollection<SimpleFeatureType, SimpleFeature> collection;
        collection = DataUtilities.collection(Arrays.asList((SimpleFeature) points1,
                (SimpleFeature) points2, (SimpleFeature) points3));

        DefaultTransaction tx = new DefaultTransaction();
        points.setTransaction(tx);
        assertSame(tx, points.getTransaction());
        try {
            points.addFeatures(collection);

            tx.putProperty(GeogigTransactionState.VERSIONING_COMMIT_AUTHOR, "John Doe");
            tx.putProperty(GeogigTransactionState.VERSIONING_COMMIT_MESSAGE, "test message");
            tx.commit();
            assertEquals(3, dataStore.getFeatureSource(pointsName).getFeatures().size());
        } catch (Exception e) {
            tx.rollback();
            throw e;
        } finally {
            tx.close();
        }

        List<RevCommit> commits = toList(repo.command(LogOp.class).call());
        assertFalse(commits.isEmpty());
        assertTrue(commits.get(0).getAuthor().getName().isPresent());
        assertEquals("John Doe", commits.get(0).getAuthor().getName().get());
        assertEquals("test message", commits.get(0).getMessage());
    }

    @Test
    public void testTransactionCommitAuthorAndEmail() throws Exception {

        FeatureCollection<SimpleFeatureType, SimpleFeature> collection;
        collection = DataUtilities.collection(Arrays.asList((SimpleFeature) points1,
                (SimpleFeature) points2, (SimpleFeature) points3));

        DefaultTransaction tx = new DefaultTransaction();
        points.setTransaction(tx);
        assertSame(tx, points.getTransaction());
        try {
            points.addFeatures(collection);

            tx.putProperty(GeogigTransactionState.VERSIONING_COMMIT_AUTHOR, "john");
            tx.putProperty(GeogigTransactionState.VERSIONING_COMMIT_MESSAGE, "test message");
            tx.putProperty("fullname", "John Doe");
            tx.putProperty("email", "jd@example.com");
            tx.commit();
            assertEquals(3, dataStore.getFeatureSource(pointsName).getFeatures().size());
        } catch (Exception e) {
            tx.rollback();
            throw e;
        } finally {
            tx.close();
        }

        List<RevCommit> commits = toList(repo.command(LogOp.class).call());
        assertFalse(commits.isEmpty());
        assertTrue(commits.get(0).getAuthor().getName().isPresent());
        assertEquals("John Doe", commits.get(0).getAuthor().getName().orElse(null));
        assertEquals("jd@example.com", commits.get(0).getAuthor().getEmail().orElse(null));
    }

    public @Test void testAddFeaturesWrongTypeName() throws Exception {

        SimpleFeatureType newType = DataUtilities.createType("http://geogig.someType", "someType",
                "sp:String,ip:Integer");

        org.locationtech.geogig.feature.Feature newFeature = feature(GT.adapt(newType),
                "someType.1", "StringProp1", new Integer(1000));

        SimpleFeatureCollection collection = collection(points1, points2, newFeature);

        expected.expect(IOException.class);
        expected.expectMessage("Tried to insert features of type 'someType' into 'Points'");
        points.addFeatures(collection);
    }

    public @Test void testAddFeaturesSubType() throws Exception {

        FeatureCollection<SimpleFeatureType, SimpleFeature> collection;
        SimpleFeatureType subType = DataUtilities.createSubType(GT.adapt(pointsType),
                new String[] { "ip" });
        FeatureType gigType = GT.adapt(subType);
        Feature newFeature = GT.adapt(subType, feature(gigType, "subtype.1", new Integer(-1)));
        newFeature.getUserData().put(Hints.USE_PROVIDED_FID, Boolean.TRUE);

        collection = DataUtilities.collection(Arrays.asList((SimpleFeature) points1,
                (SimpleFeature) points2, (SimpleFeature) newFeature));
        List<FeatureId> addFeatures = points.addFeatures(collection);
        assertEquals(3, addFeatures.size());

        assertEquals("subtype.1", addFeatures.get(2).getID());
    }

    public @Test void testAddFeaturesWrongAttributeType() throws Exception {

        final FeatureType original = pointsType;
        final String typeSpec = pointsTypeSpec + ",notInOriginalProp:String";

        final SimpleFeatureType newType = DataUtilities.createType(
                original.getName().getNamespaceURI(), original.getName().getLocalPart(), typeSpec);

        FeatureType gigType = GT.adapt(newType);

        org.locationtech.geogig.feature.Feature newFeature = feature(gigType, "someid",
                "StringProp1", new Integer(1000), (Geometry) null,
                "value of att not in target schema");

        SimpleFeatureCollection collection = collection(points1, points2, newFeature);

        expected.expect(IOException.class);
        expected.expectMessage("No such attribute:notInOriginalProp");
        points.addFeatures(collection);

    }
}
