/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geopkg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.web.api.TestData.point1;
import static org.locationtech.geogig.web.api.TestData.pointsType;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.codehaus.jettison.json.JSONObject;
import org.geotools.data.DataStore;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureStore;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GeogigTransaction;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.plumbing.LsTreeOp;
import org.locationtech.geogig.api.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.api.plumbing.TransactionBegin;
import org.locationtech.geogig.api.plumbing.TransactionEnd;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.geotools.geopkg.GeopkgAuditExport;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.AsyncContext.Status;
import org.locationtech.geogig.rest.geotools.Import;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;
import org.opengis.filter.Filter;
import org.skyscreamer.jsonassert.JSONAssert;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

/**
 *
 */
public class GeoPackageImportIntegrationTest extends AbstractWebOpTest {

    private CommandContext context;

    private AsyncContext testAsyncContext;

    @Before
    public void before() {
        context = super.testContext.get();
        testAsyncContext = AsyncContext.createNew();
    }

    @After
    public void after() {
        testAsyncContext.shutDown();
    }

    @Override
    protected String getRoute() {
        return "import";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Import.class;
    }

    @Test()
    public void testFormatArgumentRquired() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData();

        GeogigTransaction transaction = repo.command(TransactionBegin.class).call();

        Import op = buildCommand(
                TestParams.of("transactionId", transaction.getTransactionId().toString()));
        op.asyncContext = testAsyncContext;
        ex.expect(IllegalArgumentException.class);
        ex.expectMessage("missing required 'format' parameter");
        run(op);
    }

    @Test
    public void testImportAllMissingFileUpload() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData();

        GeogigTransaction transaction = repo.command(TransactionBegin.class).call();

        Import op = buildCommand("format", "gpkg", "transactionId",
                transaction.getTransactionId().toString());
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = waitForTask(result);
        Assert.assertEquals(Status.FAILED, resultStatus);
    }

    @Test
    public void testImportAllInvalidFileUpload() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData();

        GeogigTransaction transaction = repo.command(TransactionBegin.class).call();

        Import op = buildCommand("format", "gpkg", "mockFileUpload", "blah", "transactionId",
                transaction.getTransactionId().toString());
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = waitForTask(result);
        Assert.assertEquals(Status.FAILED, resultStatus);
    }

    @Test
    public void testImportTable() throws Throwable {
        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        final File dbFile = generateDbFile();

        GeogigTransaction transaction = repo.command(TransactionBegin.class).call();

        // parameter setup
        ParameterSet params = TestParams.of("format", "gpkg", "layer", "Lines", "transactionId",
                transaction.getTransactionId().toString());
        ((TestParams) params).setFileUpload(dbFile);
        TestData testData = new TestData(repo);
        testData.init();
        // verify there are no nodes in the repository
        verifyNoCommitedNodes();

        Import op = buildCommand(params);
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = waitForTask(result);
        Assert.assertEquals(Status.FINISHED, resultStatus);

        repo.command(TransactionEnd.class).setTransaction(transaction).call();
        // verify the dbFile is gone
        verifyDbFileDeleted(dbFile);
        // verify data was imported
        verifyImport(Sets.newHashSet("Lines"));
    }

    @Test
    public void testImportTableWithDest() throws Throwable {
        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        final File dbFile = generateDbFile();

        GeogigTransaction transaction = repo.command(TransactionBegin.class).call();
        // parameter setup
        ParameterSet params = TestParams.of("format", "gpkg", "layer", "Lines", "dest", "newLines",
                "transactionId", transaction.getTransactionId().toString());
        ((TestParams) params).setFileUpload(dbFile);
        TestData testData = new TestData(repo);
        testData.init();
        // verify there are no nodes in the repository
        verifyNoCommitedNodes();

        Import op = buildCommand(params);
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = waitForTask(result);
        Assert.assertEquals(Status.FINISHED, resultStatus);

        repo.command(TransactionEnd.class).setTransaction(transaction).call();
        // verify the dbFile is gone
        verifyDbFileDeleted(dbFile);
        // verify data was imported
        verifyImport(Sets.newHashSet("newLines"));
    }

    @Test
    public void testImportToBranch() throws Throwable {
        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        final File dbFile = generateDbFile();

        TestData testData = new TestData(repo);
        testData.init();
        repo.command(CommitOp.class).setAllowEmpty(true).setMessage("Initial Commit").call();
        testData.branch("branch1");

        verifyNoCommitedNodes();

        GeogigTransaction transaction = repo.command(TransactionBegin.class).call();

        // parameter setup
        ParameterSet params = TestParams.of("format", "gpkg", "root", "branch1", "layer", "Lines",
                "transactionId", transaction.getTransactionId().toString());
        ((TestParams) params).setFileUpload(dbFile);

        Import op = buildCommand(params);
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = waitForTask(result);
        Assert.assertEquals(Status.FINISHED, resultStatus);

        repo.command(TransactionEnd.class).setTransaction(transaction).call();
        // verify the dbFile is gone
        verifyDbFileDeleted(dbFile);

        // verify that the main branch has no nodes
        verifyNoCommitedNodes();

        // verify data was imported on branch1
        testData.checkout("branch1");
        verifyImport(Sets.newHashSet("Lines"));
    }

    @Test
    public void testImportTableWithDestDuplicate() throws Throwable {
        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        final File dbFile = generateDbFile();

        GeogigTransaction transaction = repo.command(TransactionBegin.class).call();
        // need to make a copy because the file will get deleted after import
        final File dbFileCopy = File.createTempFile("geogig-test-clone", ".gpkg");
        Files.copy(dbFile, dbFileCopy);
        // parameter setup
        ParameterSet params = TestParams.of("format", "gpkg", "layer", "Lines", "dest", "newLines",
                "transactionId", transaction.getTransactionId().toString());
        ((TestParams) params).setFileUpload(dbFile);
        TestData testData = new TestData(repo);
        testData.init();
        // verify there are no nodes in the repository
        verifyNoCommitedNodes();

        Import op = buildCommand(params);
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = waitForTask(result);
        Assert.assertEquals(Status.FINISHED, resultStatus);

        repo.command(TransactionEnd.class).setTransaction(transaction).call();
        // verify the dbFile is gone
        verifyDbFileDeleted(dbFile);
        // verify data was imported
        verifyImport(Sets.newHashSet("newLines"));
        // do it again but with a different dest

        transaction = repo.command(TransactionBegin.class).call();
        params = TestParams.of("format", "gpkg", "layer", "Lines", "dest", "newLines2",
                "transactionId", transaction.getTransactionId().toString());
        // set the DB file to the copy since the original should be gone
        ((TestParams) params).setFileUpload(dbFileCopy);
        op = buildCommand(params);
        op.asyncContext = testAsyncContext;
        // runing a second time, specify the expected task Id
        result = run(op, "2");
        Assert.assertNotNull(result.getStatus());
        resultStatus = waitForTask(result);
        Assert.assertEquals(Status.FINISHED, resultStatus);

        repo.command(TransactionEnd.class).setTransaction(transaction).call();
        // verify the dbFileCopy is gone
        verifyDbFileDeleted(dbFileCopy);
        // verify data was imported
        verifyImport(Sets.newHashSet("newLines", "newLines2"));
    }

    @Test
    public void testImportAll() throws Throwable {
        // get a DB file to import
        final File dbFile = generateDbFile();
        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init();
        // verify there are no nodes in the repository
        verifyNoCommitedNodes();

        GeogigTransaction transaction = repo.command(TransactionBegin.class).call();

        // now call import with the manual transaction
        ParameterSet params = TestParams.of("format", "gpkg", "transactionId",
                transaction.getTransactionId().toString());
        ((TestParams) params).setFileUpload(dbFile);
        Import op = buildCommand(params);
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = waitForTask(result);
        Assert.assertEquals(Status.FINISHED, resultStatus);
        // verify the dbFile is gone
        verifyDbFileDeleted(dbFile);
        // verify import hasn't been committed yet
        verifyNoCommitedNodes();

        repo.command(TransactionEnd.class).setTransaction(transaction).call();
        // verify data was imported
        verifyImport(Sets.newHashSet("Points", "Lines", "Polygons"));
    }

    @Test
    public void testImportInterchange() throws Throwable {
        // get a DB file to import
        GeoPackageTestSupport support = new GeoPackageTestSupport();
        File file = support.createEmptyDatabase();

        MemoryDataStore memStore = TestData.newMemoryDataStore();
        memStore.addFeatures(ImmutableList.of(TestData.point1));

        DataStore gpkgStore = support.createDataStore(file);
        try {
            support.export(memStore.getFeatureSource(pointsType.getName().getLocalPart()),
                    gpkgStore);
        } finally {
            gpkgStore.dispose();
        }

        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init();
        testData.addAndCommit("Initial commit", point1);
        
        repo.command(GeopkgAuditExport.class).setDatabase(file).setSourcePathspec("master:Points")
                .setTargetTableName("Points").call();
        
        // modify point in the geopackage
        gpkgStore = support.createDataStore(file);
        Transaction gttx = new DefaultTransaction();
        try {
            SimpleFeatureStore store = (SimpleFeatureStore) gpkgStore.getFeatureSource("Points");
            Preconditions.checkState(store.getQueryCapabilities().isUseProvidedFIDSupported());
            store.setTransaction(gttx);
            store.modifyFeatures("ip", TestData.point1_modified.getAttribute("ip"), Filter.INCLUDE);
            gttx.commit();
        } finally {
            gttx.close();
            gpkgStore.dispose();
        }

        testData.addAndCommit("Add point2", TestData.point2);
        

        GeogigTransaction transaction = repo.command(TransactionBegin.class).call();

        // now call import with the manual transaction
        ParameterSet params = TestParams.of("interchange", "true", "authorName", "Tester",
                "authorEmail", "tester@example.com", "format", "gpkg", "message",
                "Imported geopackage.", "layer", "Points", "transactionId",
                transaction.getTransactionId().toString());
        ((TestParams) params).setFileUpload(file);
        Import op = buildCommand(params);
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = waitForTask(result);
        Assert.assertEquals(Status.FINISHED, resultStatus);

        Object resultObject = result.get();
        assertTrue(resultObject instanceof RevCommit);

        RevCommit mergeCommit = (RevCommit) resultObject;
        assertEquals("Merge: Imported geopackage.", mergeCommit.getMessage());
        assertEquals(2, mergeCommit.getParentIds().size());
        assertEquals("Tester", mergeCommit.getAuthor().getName().get());
        assertEquals("tester@example.com", mergeCommit.getAuthor().getEmail().get());

        repo.command(TransactionEnd.class).setTransaction(transaction).call();

        // verify both points are in the repo.
        Iterator<NodeRef> nodeIterator = context.getGeoGIG().command(LsTreeOp.class)
                .setReference("Points").setStrategy(Strategy.FEATURES_ONLY).call();

        List<String> nodeList = Lists.transform(Lists.newArrayList(nodeIterator),
                (nr) -> nr.name());
        assertEquals(2, nodeList.size());
        assertTrue(nodeList.contains("1"));
        assertTrue(nodeList.contains("2"));
    }

    @Test
    public void testImportInterchangeOnBranch() throws Throwable {
        // get a DB file to import
        GeoPackageTestSupport support = new GeoPackageTestSupport();
        File file = support.createEmptyDatabase();

        MemoryDataStore memStore = TestData.newMemoryDataStore();
        memStore.addFeatures(ImmutableList.of(TestData.point1));

        DataStore gpkgStore = support.createDataStore(file);
        try {
            support.export(memStore.getFeatureSource(pointsType.getName().getLocalPart()),
                    gpkgStore);
        } finally {
            gpkgStore.dispose();
        }

        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init();
        repo.command(CommitOp.class).setAllowEmpty(true).setMessage("Initial Commit").call();
        testData.branchAndCheckout("branch1");
        testData.addAndCommit("Point1", point1);

        repo.command(GeopkgAuditExport.class).setDatabase(file).setSourcePathspec("Points")
                .setTargetTableName("Points").call();

        // modify point in the geopackage
        gpkgStore = support.createDataStore(file);
        Transaction gttx = new DefaultTransaction();
        try {
            SimpleFeatureStore store = (SimpleFeatureStore) gpkgStore.getFeatureSource("Points");
            Preconditions.checkState(store.getQueryCapabilities().isUseProvidedFIDSupported());
            store.setTransaction(gttx);
            store.modifyFeatures("ip", TestData.point1_modified.getAttribute("ip"), Filter.INCLUDE);
            gttx.commit();
        } finally {
            gttx.close();
            gpkgStore.dispose();
        }

        testData.addAndCommit("Add point2", TestData.point2);

        testData.checkout("master");

        GeogigTransaction transaction = repo.command(TransactionBegin.class).call();

        // now call import with the manual transaction
        ParameterSet params = TestParams.of("interchange", "true", "root", "branch1", "authorName",
                "Tester", "authorEmail", "tester@example.com", "format", "gpkg", "message",
                "Imported geopackage.", "layer", "Points", "transactionId",
                transaction.getTransactionId().toString());
        ((TestParams) params).setFileUpload(file);
        Import op = buildCommand(params);
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = waitForTask(result);
        Assert.assertEquals(Status.FINISHED, resultStatus);

        Object resultObject = result.get();
        assertTrue(resultObject instanceof RevCommit);

        RevCommit mergeCommit = (RevCommit) resultObject;
        assertEquals("Merge: Imported geopackage.", mergeCommit.getMessage());
        assertEquals(2, mergeCommit.getParentIds().size());
        assertEquals("Tester", mergeCommit.getAuthor().getName().get());
        assertEquals("tester@example.com", mergeCommit.getAuthor().getEmail().get());

        repo.command(TransactionEnd.class).setTransaction(transaction).call();

        // verify there are no features on the master (current) branch
        verifyNoCommitedNodes();

        // verify both points are in the repo under branch1.
        testData.checkout("branch1");
        Iterator<NodeRef> nodeIterator = context.getGeoGIG().command(LsTreeOp.class)
                .setReference("Points").setStrategy(Strategy.FEATURES_ONLY).call();

        List<String> nodeList = Lists.transform(Lists.newArrayList(nodeIterator),
                (nr) -> nr.name());
        assertEquals(2, nodeList.size());
        assertTrue(nodeList.contains("1"));
        assertTrue(nodeList.contains("2"));
    }

    @Test
    public void testImportInterchangeConflicts() throws Throwable {
        // get a DB file to import
        GeoPackageTestSupport support = new GeoPackageTestSupport();
        File file = support.createEmptyDatabase();

        MemoryDataStore memStore = TestData.newMemoryDataStore();
        memStore.addFeatures(ImmutableList.of(TestData.point1));

        DataStore gpkgStore = support.createDataStore(file);
        try {
            support.export(memStore.getFeatureSource(pointsType.getName().getLocalPart()),
                    gpkgStore);
        } finally {
            gpkgStore.dispose();
        }

        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init();
        testData.addAndCommit("Initial commit", TestData.point1);

        repo.command(GeopkgAuditExport.class).setDatabase(file).setSourcePathspec("master:Points")
                .setTargetTableName("Points").call();

        // modify point in the geopackage
        gpkgStore = support.createDataStore(file);
        Transaction gttx = new DefaultTransaction();
        try {
            SimpleFeatureStore store = (SimpleFeatureStore) gpkgStore.getFeatureSource("Points");
            Preconditions.checkState(store.getQueryCapabilities().isUseProvidedFIDSupported());
            store.setTransaction(gttx);
            store.modifyFeatures("ip", TestData.point1_modified.getAttribute("ip"), Filter.INCLUDE);
            gttx.commit();
        } finally {
            gttx.close();
            gpkgStore.dispose();
        }

        testData.remove(TestData.point1);
        testData.add();
        testData.commit("Removed point1");

        GeogigTransaction transaction = repo.command(TransactionBegin.class).call();

        // now call import with the manual transaction
        ParameterSet params = TestParams.of("interchange", "true", "authorName", "Tester",
                "authorEmail", "tester@example.com", "format", "gpkg", "message",
                "Imported geopackage.", "layer", "Points", "transactionId",
                transaction.getTransactionId().toString());
        ((TestParams) params).setFileUpload(file);
        Import op = buildCommand(params);
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = waitForTask(result);
        Assert.assertEquals(Status.FAILED, resultStatus);

        JSONObject response = getJSONResponse();
        JSONObject task = response.getJSONObject("task");
        JSONObject merge = task.getJSONObject("result").getJSONObject("Merge");
        assertEquals(1, merge.getInt("conflicts"));
        JSONObject conflictedFeature = merge.getJSONObject("Feature");
        assertEquals("CONFLICT", conflictedFeature.getString("change"));
        assertEquals("Points/1", conflictedFeature.getString("id"));
    }

    private Status waitForTask(AsyncContext.AsyncCommand<?> result) {
        Status resultStatus = result.getStatus();
        while (!resultStatus.isTerminated()) {
            Thread.yield();
            resultStatus = result.getStatus();
        }
        return resultStatus;
    }

    private void verifyDbFileDeleted(final File dbFile) {
        Assert.assertNotNull("DB File should NOT be null", dbFile);
        Assert.assertFalse("DB File should NOT still exist", dbFile.exists());
    }

    private void verifyImport(Set<String> layerNames) {
        // get the list
        Iterator<NodeRef> nodeIterator = context.getGeoGIG().command(LsTreeOp.class).call();
        Assert.assertTrue("Expected repo to have some nodes, but was empty",
                nodeIterator.hasNext());
        List<String> nodeList = Lists.transform(Lists.newArrayList(nodeIterator),
                (nr) -> nr.name());
        for (String layerName : layerNames) {
            Assert.assertTrue("Expected layer \"" + layerName + "\" to exist in repo",
                    nodeList.contains(layerName));
        }
    }

    private void verifyNoCommitedNodes() {
        Iterator<NodeRef> nodeIterator = context.getGeoGIG().command(LsTreeOp.class).call();
        Assert.assertFalse("Expected repo to be empty, but has nodes", nodeIterator.hasNext());
    }

    private AsyncContext.AsyncCommand<?> run(Import op)
            throws JSONException, InterruptedException, ExecutionException {
        return run(op, "1");
    }

    private AsyncContext.AsyncCommand<?> run(Import op, String taskId)
            throws JSONException, InterruptedException, ExecutionException {
        op.run(context);
        JSONObject response = getJSONResponse();
        JSONAssert.assertEquals(String.format(
                "{'task':{'id':%s,'description':'Importing GeoPackage database file.','href':'/geogig/tasks/%s.json'}}",
                taskId, taskId), response.toString(), false);
        Optional<AsyncContext.AsyncCommand<?>> asyncCommand = Optional.absent();
        while (!asyncCommand.isPresent()) {
            asyncCommand = testAsyncContext.getAndPruneIfFinished(taskId);
        }
        Assert.assertNotNull(asyncCommand);
        Assert.assertNotNull(asyncCommand.get());
        return asyncCommand.get();
    }

    private File generateDbFile() throws Exception {
        GeoPackageTestSupport support = new GeoPackageTestSupport();

        File file = support.createDefaultTestData();

        return file;
    }
}
