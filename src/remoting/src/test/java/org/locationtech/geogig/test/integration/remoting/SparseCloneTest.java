/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation as part of Fetch/ClopeOpTest
 * Gabiel Roldan (Boundless) refactored from Fetch/CloneOpTest into this sparse clone specific test suite
 */
package org.locationtech.geogig.test.integration.remoting;

import static com.google.common.collect.Lists.newArrayList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.junit.Ignore;
import org.junit.Test;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.FeatureTypes;
import org.locationtech.geogig.feature.Name;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.remotes.RemoteResolve;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.remotes.PullOp;
import org.locationtech.geogig.remotes.PushOp;
import org.locationtech.geogig.remotes.internal.AbstractMappedRemoteRepo;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.impl.Blobs;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

@Ignore // sparse cloning not really supported yet
public class SparseCloneTest extends RemoteRepositoryTestCase {

    protected static final String idR1 = "Roads.1";

    protected static final String idR2 = "Roads.2";

    protected static final String idR3 = "Roads.3";

    protected static final String idC1 = "Cities.1";

    protected static final String idC2 = "Cities.2";

    protected static final String idC3 = "Cities.3";

    protected static final String citiesNs = "http://geogig.cities";

    protected static final String citiesName = "Cities";

    protected static final String citiesTypeSpec = "name:String,population:Integer,pp:Point:srid=4326";

    protected static final Name citiesTypeName = Name.valueOf(citiesNs, citiesName);

    protected FeatureType citiesType;

    protected Feature city1;

    protected Feature city1_modified;

    protected Feature city2;

    protected Feature city3;

    protected static final String roadsNs = "http://geogig.roads";

    protected static final String roadsName = "Roads";

    protected static final String roadsTypeSpec = "name:String,length:Integer,pp:LineString:srid=4326";

    protected static final Name roadsTypeName = Name.valueOf(roadsNs, roadsName);

    protected FeatureType roadsType;

    protected Feature road1;

    protected Feature road2;

    protected Feature road3;

    protected @Override void setUpInternal() throws Exception {
        citiesType = FeatureTypes.createType(citiesTypeName.toString(), citiesTypeSpec.split(","));

        city1 = feature(citiesType, idC1, "San Francisco", Integer.valueOf(200000),
                "POINT(10.5559899 -71.6524294)");
        city1_modified = feature(citiesType, idC1, "San Francisco", Integer.valueOf(200000),
                "POINT(37.76169 -122.44791)");
        city2 = feature(citiesType, idC2, "San Diego", Integer.valueOf(350000),
                "POINT(32.7443 -117.2157)");
        city3 = feature(citiesType, idC3, "Los Angeles", Integer.valueOf(1000000),
                "POINT(34.0455 -118.2380)");

        roadsType = FeatureTypes.createType(roadsTypeName.toString(), roadsTypeSpec.split(","));

        road1 = feature(roadsType, idR1, "Main Street", Integer.valueOf(236),
                "LINESTRING (37.76169 -122.44791, 34.0455 -118.2380)");
        road2 = feature(roadsType, idR2, "Long Road", Integer.valueOf(2845),
                "LINESTRING (37.76169 -122.44791, 32.7443 -117.2157)");
        road3 = feature(roadsType, idR3, "San Rafael Way", Integer.valueOf(528),
                "LINESTRING (34.0455 -118.2380, 37.76169 -122.44791)");
    }

    private void createFilterFile(Map<String, String> filters) {
        String filterBlob = "";
        for (Entry<String, String> entry : filters.entrySet()) {
            String featurePath = entry.getKey();
            String filter = entry.getValue();
            filterBlob += "[" + featurePath + "]\n";
            filterBlob += "type = CQL\n";
            filterBlob += "filter = " + filter + "\n";
        }
        try {
            localRepo.context().blobStore().putBlob(Blobs.SPARSE_FILTER_BLOB_KEY,
                    filterBlob.getBytes());
            Optional<Remote> remoteInfo = localRepo.command(RemoteResolve.class)
                    .setName(REMOTE_NAME).call();
            Preconditions.checkState(remoteInfo.isPresent());
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    public void testSparseClone() throws Exception {

        Map<String, String> filter = new HashMap<String, String>();
        filter.put("default", "BBOX(pp,30, -125, 40, -110,'EPSG:4326')");
        filter.put("Cities", "BBOX(pp,33, -125, 40, -110,'EPSG:4326')");
        createFilterFile(filter);
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(city1, city2, city3, road1, road2, road3);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();
        Map<Feature, ObjectId> oids = new HashMap<Feature, ObjectId>();

        for (Feature f : features) {
            ObjectId oId = insertAndAdd(originRepo, f);
            oids.put(f, oId);
            final RevCommit commit = originRepo.command(CommitOp.class).setMessage(f.getId())
                    .call();
            expected.addFirst(commit);
            Optional<RevObject> childObject = originRepo.command(RevObjectParse.class)
                    .setObjectId(oId).call();
            assertTrue(childObject.isPresent());
        }

        // Make sure the remote has all of the commits
        List<RevCommit> logged = newArrayList(originRepo.command(LogOp.class).call());

        assertEquals(expected, logged);

        // Make sure the local repository has no commits prior to clone
        assertFalse(localRepo.command(LogOp.class).call().hasNext());

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setDepth(0);
        clone.setRemoteURI(originRepo.getLocation()).setBranch("master").call();

        // The features that match the filter are "Cities.3", "Roads.1", "Roads.2", and "Roads.3",
        // the "Cities.1" commit should be present since it added the "Cities" tree, but "Cities.1"
        // should not be present in the tree.

        // Make sure the local repository got the correct commits
        logged = newArrayList(localRepo.command(LogOp.class).call());

        assertEquals(5, logged.size());
        assertEquals("Roads.3", logged.get(0).getMessage());
        assertFalse(expected.get(0).getId().equals(logged.get(0).getId()));
        assertEquals("Roads.2", logged.get(1).getMessage());
        assertFalse(expected.get(1).getId().equals(logged.get(1).getId()));
        assertEquals("Roads.1", logged.get(2).getMessage());
        assertFalse(expected.get(2).getId().equals(logged.get(2).getId()));
        assertEquals("Cities.3", logged.get(3).getMessage());
        assertFalse(expected.get(3).getId().equals(logged.get(3).getId()));
        assertEquals("Cities.1", logged.get(4).getMessage());
        assertFalse(expected.get(5).getId().equals(logged.get(4).getId()));

        assertExists(localRepo, oids.get(city3), oids.get(road1), oids.get(road2), oids.get(road3));
        assertNotExists(localRepo, oids.get(city1), oids.get(city2));
    }

    @Test
    public void testSparseCloneAllMatch() throws Exception {

        Map<String, String> filter = new HashMap<String, String>();
        filter.put("default", "BBOX(pp,0, -125, 40, -70,'EPSG:4326')");
        createFilterFile(filter);
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(city1, city2, city3, road1, road2, road3);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();
        Map<Feature, ObjectId> oids = new HashMap<Feature, ObjectId>();

        for (Feature f : features) {
            ObjectId oId = insertAndAdd(originRepo, f);
            oids.put(f, oId);
            final RevCommit commit = originRepo.command(CommitOp.class).setMessage(f.getId())
                    .call();
            expected.addFirst(commit);
            Optional<RevObject> childObject = originRepo.command(RevObjectParse.class)
                    .setObjectId(oId).call();
            assertTrue(childObject.isPresent());
        }

        // Make sure the remote has all of the commits
        List<RevCommit> logged = newArrayList(originRepo.command(LogOp.class).call());

        assertEquals(expected, logged);

        // Make sure the local repository has no commits prior to clone
        logged = newArrayList(localRepo.command(LogOp.class).call());
        assertTrue(logged.isEmpty());

        // clone from the remote
        CloneOp clone = cloneOp();
        // clone.setDepth(0);
        clone.setRemoteURI(originRepo.getLocation()).setBranch("master").call();

        // Because all features match the filter, the history should be identical

        // Make sure the local repository got the correct commits
        logged = newArrayList(localRepo.command(LogOp.class).call());

        List<ObjectId> expectedTrees = Lists.transform(expected, (c) -> c.getTreeId());
        List<ObjectId> actualTrees = Lists.transform(logged, (c) -> c.getTreeId());
        assertEquals(expectedTrees, actualTrees);

        assertEquals(expected, logged);

        assertExists(localRepo, oids.get(city1), oids.get(city2), oids.get(city3), oids.get(road1),
                oids.get(road2), oids.get(road3));
    }

    @Test
    public void testSparseCloneOnlyFirstMatch() throws Exception {

        Map<String, String> filter = new HashMap<String, String>();
        filter.put("default", "BBOX(pp,9, -80, 15, -70,'EPSG:4326')");
        createFilterFile(filter);
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(city1, city2, city3, road1, road2, road3);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();
        Map<Feature, ObjectId> oids = new HashMap<Feature, ObjectId>();

        for (Feature f : features) {
            ObjectId oId = insertAndAdd(originRepo, f);
            oids.put(f, oId);
            final RevCommit commit = originRepo.command(CommitOp.class).setMessage(f.getId())
                    .call();
            expected.addFirst(commit);
            Optional<RevObject> childObject = originRepo.command(RevObjectParse.class)
                    .setObjectId(oId).call();
            assertTrue(childObject.isPresent());
        }

        // Make sure the remote has all of the commits
        List<RevCommit> logged = newArrayList(originRepo.command(LogOp.class).call());

        assertEquals(expected, logged);

        // Make sure the local repository has no commits prior to clone
        assertFalse(localRepo.command(LogOp.class).call().hasNext());

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setDepth(0);
        clone.setRemoteURI(originRepo.getLocation()).setBranch("master").call();

        // Because only the first feature matches (Cities.1), the first commit should be the same,
        // there will also be the commit that adds the "Roads" tree but no features, and finally an
        // "Empty Placeholder Commit".

        // Make sure the local repository got the correct commits
        logged = newArrayList(localRepo.command(LogOp.class).call());

        assertEquals(3, logged.size());
        assertEquals(AbstractMappedRemoteRepo.PLACEHOLDER_COMMIT_MESSAGE,
                logged.get(0).getMessage());
        assertFalse(expected.get(0).getId().equals(logged.get(0).getId()));
        assertEquals("Roads.1", logged.get(1).getMessage());
        assertFalse(expected.get(2).getId().equals(logged.get(1).getId()));
        assertEquals("Cities.1", logged.get(2).getMessage());
        assertTrue(expected.get(5).getId().equals(logged.get(2).getId()));

        assertExists(localRepo, oids.get(city1));
        assertNotExists(localRepo, oids.get(city2), oids.get(city3), oids.get(road1),
                oids.get(road2), oids.get(road3));
    }

    @Test
    public void testFeatureMovingOutOfAOI() throws Exception {

        Map<String, String> filter = new HashMap<String, String>();
        filter.put("default", "BBOX(pp,9, -80, 15, -70,'EPSG:4326')");
        createFilterFile(filter);
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(city1, city1_modified);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();
        Map<Feature, ObjectId> oids = new HashMap<Feature, ObjectId>();

        for (Feature f : features) {
            ObjectId oId = insertAndAdd(originRepo, f);
            oids.put(f, oId);
            final RevCommit commit = originRepo.command(CommitOp.class).setMessage(f.getId())
                    .call();
            expected.addFirst(commit);
            Optional<RevObject> childObject = originRepo.command(RevObjectParse.class)
                    .setObjectId(oId).call();
            assertTrue(childObject.isPresent());
        }

        // Make sure the remote has all of the commits
        List<RevCommit> logged = newArrayList(originRepo.command(LogOp.class).call());

        assertEquals(expected, logged);

        // Make sure the local repository has no commits prior to clone
        assertFalse(localRepo.command(LogOp.class).call().hasNext());

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setDepth(0);
        clone.setRemoteURI(originRepo.getLocation()).setBranch("master").call();

        // Because Cities.1 is first in our filter, then is modified to be outside the filter, it
        // should continue to be tracked. Therefore our histories should match.

        // Make sure the local repository got the correct commits
        logged = newArrayList(localRepo.command(LogOp.class).call());

        assertEquals(expected, logged);

        assertExists(localRepo, oids.get(city1), oids.get(city1_modified));
    }

    @Test
    public void testFeatureMovingIntoAOI() throws Exception {

        Map<String, String> filter = new HashMap<String, String>();
        filter.put("Cities", "BBOX(pp,30, -125, 40, -110,'EPSG:4326')");
        createFilterFile(filter);
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(city2, city1, city3, city1_modified);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();
        Map<Feature, ObjectId> oids = new HashMap<Feature, ObjectId>();

        for (Feature f : features) {
            ObjectId oId = insertAndAdd(originRepo, f);
            oids.put(f, oId);
            final RevCommit commit = originRepo.command(CommitOp.class).setMessage(f.getId())
                    .call();
            expected.addFirst(commit);
            Optional<RevObject> childObject = originRepo.command(RevObjectParse.class)
                    .setObjectId(oId).call();
            assertTrue(childObject.isPresent());
        }

        // Make sure the remote has all of the commits
        List<RevCommit> logged = newArrayList(originRepo.command(LogOp.class).call());

        assertEquals(expected, logged);

        // Make sure the local repository has no commits prior to clone
        assertFalse(localRepo.command(LogOp.class).call().hasNext());

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setDepth(0);
        clone.setRemoteURI(originRepo.getLocation()).setBranch("master").call();

        // Cities.1 initially lies outside the filter, so the commit that adds it will not be part
        // of the sparse clone. Later the feature is moved into the AOI so it will be added at that
        // time.

        // Make sure the local repository got the correct commits
        logged = newArrayList(localRepo.command(LogOp.class).call());

        assertEquals(3, logged.size());
        assertEquals("Cities.1", logged.get(0).getMessage());
        assertFalse(expected.get(0).getId().equals(logged.get(0).getId()));
        assertEquals("Cities.3", logged.get(1).getMessage());
        assertFalse(expected.get(1).getId().equals(logged.get(1).getId()));
        assertEquals("Cities.2", logged.get(2).getMessage());
        assertTrue(expected.get(3).getId().equals(logged.get(2).getId()));

        assertExists(localRepo, oids.get(city2), oids.get(city3), oids.get(city1_modified));
        assertNotExists(localRepo, oids.get(city1));
    }

    @Test
    public void testPullCommitThatPassesFilter() throws Exception {
        setupSparseClone();
        // Add a commit that passes our filter to the remote.
        ObjectId oId = insertAndAdd(originRepo, city1_modified);
        final RevCommit commit = originRepo.command(CommitOp.class)
                .setMessage(city1_modified.getId()).call();
        Optional<RevObject> childObject = originRepo.command(RevObjectParse.class).setObjectId(oId)
                .call();
        assertTrue(childObject.isPresent());
        assertEquals(commit, originRepo.context().objectDatabase().getCommit(commit.getId()));

        PullOp pull = pullOp();
        pull.call();

        List<RevCommit> logged = newArrayList(localRepo.command(LogOp.class).call());

        assertEquals("Cities.1", logged.get(0).getMessage());
        assertFalse(commit.getId().equals(logged.get(0).getId()));

        assertExists(localRepo, oId);
    }

    @Test
    public void testPullCommitThatDoesNotPassFilter() throws Exception {
        setupSparseClone();
        // Add a commit that passes our filter to the remote.
        ObjectId oId = insertAndAdd(originRepo, city1);
        final RevCommit commit = originRepo.command(CommitOp.class).setMessage(city1.getId())
                .call();
        Optional<RevObject> childObject = originRepo.command(RevObjectParse.class).setObjectId(oId)
                .call();
        assertTrue(childObject.isPresent());

        PullOp pull = pullOp();
        pull.call();

        List<RevCommit> logged = newArrayList(localRepo.command(LogOp.class).call());

        assertEquals(AbstractMappedRemoteRepo.PLACEHOLDER_COMMIT_MESSAGE,
                logged.get(0).getMessage());
        assertFalse(commit.getId().equals(logged.get(0).getId()));

        assertNotExists(localRepo, oId);
    }

    @Test
    public void testPushCommitsFromSparseClone() throws Exception {
        setupSparseClone();
        // Add some commits to the local (sparse) repository
        List<Feature> features = Arrays.asList(city1, city1_modified, road3);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();
        Map<Feature, ObjectId> oids = new HashMap<Feature, ObjectId>();

        for (Feature f : features) {
            ObjectId oId = insertAndAdd(localRepo, f);
            oids.put(f, oId);
            final RevCommit commit = localRepo.command(CommitOp.class).setMessage(f.getId()).call();
            expected.addFirst(commit);
            Optional<RevObject> childObject = localRepo.command(RevObjectParse.class)
                    .setObjectId(oId).call();
            assertTrue(childObject.isPresent());
        }

        PushOp push = pushOp();
        push.setAll(true).call();

        List<RevCommit> logged = newArrayList(originRepo.command(LogOp.class).call());

        assertEquals("Roads.3", logged.get(0).getMessage());
        assertFalse(expected.get(0).getId().equals(logged.get(0).getId()));
        assertEquals("Cities.1", logged.get(1).getMessage());
        assertFalse(expected.get(1).getId().equals(logged.get(1).getId()));
        assertEquals("Cities.1", logged.get(2).getMessage());
        assertFalse(expected.get(2).getId().equals(logged.get(2).getId()));

        assertExists(originRepo, oids.get(city1), oids.get(city1_modified), oids.get(road3));
    }

    @Test
    public void testPushSparseMerge() throws Exception {
        setupSparseClone();
        // create a branch off an early commit
        Iterator<RevCommit> logs = localRepo.command(LogOp.class).call();
        RevCommit initialCommit = logs.next();
        ObjectId masterCommit = initialCommit.getId();
        while (logs.hasNext()) {
            initialCommit = logs.next();
        }
        localRepo.command(BranchCreateOp.class).setName("Branch1").setAutoCheckout(true)
                .setSource(initialCommit.getId().toString()).call();

        // Add some commits to the local (sparse) repository
        List<Feature> features = Arrays.asList(city1, city1_modified, road3);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();
        Map<Feature, ObjectId> oids = new HashMap<Feature, ObjectId>();

        for (Feature f : features) {
            ObjectId oId = insertAndAdd(localRepo, f);
            oids.put(f, oId);
            final RevCommit commit = localRepo.command(CommitOp.class).setMessage(f.getId()).call();
            expected.addFirst(commit);
            Optional<RevObject> childObject = localRepo.command(RevObjectParse.class)
                    .setObjectId(oId).call();
            assertTrue(childObject.isPresent());
        }

        // Merge master into Branch1
        MergeOp merge = localRepo.command(MergeOp.class);
        MergeReport report = merge.addCommit(masterCommit).setMessage("Merge").call();

        // Update master to the new merge commit
        localRepo.command(UpdateRef.class).setName("refs/heads/master")
                .setNewValue(report.getMergeCommit().getId()).call();

        // Checkout master
        localRepo.command(CheckoutOp.class).setSource("master").call();

        PushOp push = pushOp();
        push.addRefSpec("refs/heads/master").call();

        logs = originRepo.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals("Merge", logged.get(0).getMessage());
        assertFalse(report.getMergeCommit().getId().equals(logged.get(0).getId()));

        // Although we merged "Roads.2" commit into the "Roads.3" commit, making the "Roads.2"
        // commit the second parent, they should have been swapped when pushing to the full
        // repository to prevent any sparse data from being lost.
        ObjectId parent1Id = logged.get(0).getParentIds().get(0);
        ObjectId parent2Id = logged.get(0).getParentIds().get(1);

        RevCommit parent1 = originRepo.context().objectDatabase().getCommit(parent1Id);
        assertNotNull(parent1);
        assertEquals("Roads.2", parent1.getMessage());
        RevCommit parent2 = originRepo.context().objectDatabase().getCommit(parent2Id);
        assertNotNull(parent2);
        assertEquals("Roads.3", parent2.getMessage());

        // Verify they weren't swapped in the original
        parent1Id = report.getMergeCommit().getParentIds().get(0);
        parent2Id = report.getMergeCommit().getParentIds().get(1);

        parent1 = localRepo.context().objectDatabase().getCommit(parent1Id);
        assertNotNull(parent1);
        assertEquals("Roads.3", parent1.getMessage());
        parent2 = localRepo.context().objectDatabase().getCommit(parent2Id);
        assertNotNull(parent2);
        assertEquals("Roads.2", parent2.getMessage());

        assertExists(originRepo, oids.get(city1), oids.get(city1_modified), oids.get(road3));
    }

    @Test
    public void testPushSparseMergeScenario2() throws Exception {
        setupSparseClone();
        // create a branch off an early commit
        Iterator<RevCommit> logs = localRepo.command(LogOp.class).call();
        RevCommit initialCommit = logs.next();
        while (logs.hasNext()) {
            initialCommit = logs.next();
        }
        localRepo.command(BranchCreateOp.class).setName("Branch1").setAutoCheckout(true)
                .setSource(initialCommit.getId().toString()).call();

        // Add some commits to the local (sparse) repository
        List<Feature> features = Arrays.asList(city1, city1_modified, road3);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();
        Map<Feature, ObjectId> oids = new HashMap<Feature, ObjectId>();

        for (Feature f : features) {
            ObjectId oId = insertAndAdd(localRepo, f);
            oids.put(f, oId);
            final RevCommit commit = localRepo.command(CommitOp.class).setMessage(f.getId()).call();
            expected.addFirst(commit);
            Optional<RevObject> childObject = localRepo.command(RevObjectParse.class)
                    .setObjectId(oId).call();
            assertTrue(childObject.isPresent());
        }

        // Checkout master
        localRepo.command(CheckoutOp.class).setSource("master").call();

        // Merge Branch1 into master
        MergeOp merge = localRepo.command(MergeOp.class);
        MergeReport report = merge.addCommit(expected.get(0).getId()).setMessage("Merge").call();

        PushOp push = pushOp();
        push.addRefSpec("refs/heads/master").call();

        logs = originRepo.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals("Merge", logged.get(0).getMessage());
        assertFalse(report.getMergeCommit().getId().equals(logged.get(0).getId()));

        // Because we merged Branch1 into the "sparse" master, we don't need to swap the parents, so
        // the history should look the same.
        ObjectId parent1Id = logged.get(0).getParentIds().get(0);
        ObjectId parent2Id = logged.get(0).getParentIds().get(1);

        RevCommit parent1 = originRepo.context().objectDatabase().getCommit(parent1Id);
        assertNotNull(parent1);
        assertEquals("Roads.2", parent1.getMessage());
        RevCommit parent2 = originRepo.context().objectDatabase().getCommit(parent2Id);
        assertNotNull(parent2);
        assertEquals("Roads.3", parent2.getMessage());

        // Verify they weren't swapped in the original
        parent1Id = report.getMergeCommit().getParentIds().get(0);
        parent2Id = report.getMergeCommit().getParentIds().get(1);

        parent1 = localRepo.context().objectDatabase().getCommit(parent1Id);
        assertNotNull(parent1);
        assertEquals("Roads.2", parent1.getMessage());
        parent2 = localRepo.context().objectDatabase().getCommit(parent2Id);
        assertNotNull(parent2);
        assertEquals("Roads.3", parent2.getMessage());

        assertExists(originRepo, oids.get(city1), oids.get(city1_modified), oids.get(road3));
    }

    @Test
    public void testSparseCloneWithNoBranchSpecified() throws Exception {
        Map<String, String> filter = new HashMap<String, String>();
        filter.put("default", "BBOX(pp,9, -80, 15, -70,'EPSG:4326')");
        createFilterFile(filter);

        CloneOp clone = cloneOp();
        assertThrows(IllegalArgumentException.class,
                clone.setRemoteURI(originRepo.getLocation())::call);
    }

    @Test
    public void testSparseShallowClone() throws Exception {
        Map<String, String> filter = new HashMap<String, String>();
        filter.put("default", "BBOX(pp,9, -80, 15, -70,'EPSG:4326')");
        createFilterFile(filter);

        CloneOp clone = cloneOp();
        clone.setDepth(3).setBranch("master");
        assertThrows(IllegalStateException.class,
                clone.setRemoteURI(originRepo.getLocation())::call);
    }

    private void setupSparseClone() throws Exception {

        Map<String, String> filter = new HashMap<String, String>();
        filter.put("default", "BBOX(pp,30, -125, 40, -110,'EPSG:4326')");
        filter.put("Cities", "BBOX(pp,33, -125, 40, -110,'EPSG:4326')");
        createFilterFile(filter);
        // Commit several features to the remote
        List<Feature> features = Arrays.asList(city3, road1, city2, road2);
        LinkedList<RevCommit> expected = new LinkedList<RevCommit>();
        Map<Feature, ObjectId> oids = new HashMap<Feature, ObjectId>();

        for (Feature f : features) {
            ObjectId oId = insertAndAdd(originRepo, f);
            oids.put(f, oId);
            final RevCommit commit = originRepo.command(CommitOp.class).setMessage(f.getId())
                    .call();
            expected.addFirst(commit);
            Optional<RevObject> childObject = originRepo.command(RevObjectParse.class)
                    .setObjectId(oId).call();
            assertTrue(childObject.isPresent());
        }

        // Make sure the remote has all of the commits
        Iterator<RevCommit> logs = originRepo.command(LogOp.class).call();
        List<RevCommit> logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals(expected, logged);

        // Make sure the local repository has no commits prior to clone
        logs = localRepo.command(LogOp.class).call();
        assertNotNull(logs);
        assertFalse(logs.hasNext());

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setDepth(0);
        clone.setRemoteURI(originRepo.getLocation()).setBranch("master").call();

        logs = localRepo.command(LogOp.class).call();
        logged = new ArrayList<RevCommit>();
        for (; logs.hasNext();) {
            logged.add(logs.next());
        }

        assertEquals("Roads.2", logged.get(0).getMessage());
        assertFalse(expected.get(0).getId().equals(logged.get(0).getId()));
        assertEquals(expected.get(2).getId(), logged.get(1).getId());
        assertEquals(expected.get(3).getId(), logged.get(2).getId());

    }

    private void assertExists(Repository repo, ObjectId... features) {
        Geogig gig = Geogig.of(repo.context());
        for (ObjectId object : features) {
            assertTrue(gig.objects().exists(object));
        }
    }

    private void assertNotExists(Repository repo, ObjectId... features) {
        Geogig gig = Geogig.of(repo.context());
        for (ObjectId object : features) {
            assertFalse(gig.objects().exists(object));
        }
    }

}
