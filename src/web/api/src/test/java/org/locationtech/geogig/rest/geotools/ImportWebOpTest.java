/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geotools;

import static org.locationtech.geogig.web.api.TestData.linesType;
import static org.locationtech.geogig.web.api.TestData.pointsType;
import static org.locationtech.geogig.web.api.TestData.polysType;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.codehaus.jettison.json.JSONObject;
import org.geotools.data.DataStore;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.plumbing.LsTreeOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.RemoveOp;
import org.locationtech.geogig.api.porcelain.StatusOp;
import org.locationtech.geogig.api.porcelain.StatusOp.StatusSummary;
import org.locationtech.geogig.geotools.geopkg.GeopkgDataStoreExportOp;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.AsyncContext.Status;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandBuilder;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;
import org.skyscreamer.jsonassert.JSONAssert;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

/**
 *
 */
public class ImportWebOpTest extends AbstractWebOpTest {

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
        return ImportWebOp.class;
    }

    @Test()
    public void testImportDefaults() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData();

        ImportWebOp op = buildCommand(TestParams.of());
        op.asyncContext = testAsyncContext;
        ex.expect(CommandSpecException.class);
        ex.expectMessage("missing required \"format\" parameter");
        run(op);
    }

    @Test
    public void testImportAllMissingFileUpload() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData();

        ImportWebOp op = buildCommand("format", "gpkg");
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = result.getStatus();
        while (Status.RUNNING.equals(resultStatus) || Status.WAITING.equals(resultStatus)) {
            Thread.yield();
            resultStatus = result.getStatus();
        }
        Assert.assertEquals(Status.FAILED, resultStatus);
    }

    @Test
    public void testImportAllInvalidFileUpload() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData();

        ImportWebOp op = buildCommand("format", "gpkg", "mockFileUpload", "blah");
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = result.getStatus();
        while (Status.RUNNING.equals(resultStatus) || Status.WAITING.equals(resultStatus)) {
            Thread.yield();
            resultStatus = result.getStatus();
        }
        Assert.assertEquals(Status.FAILED, resultStatus);
    }

    @Test
    public void testImportAll() throws Throwable {
        final File dbFile = generateDbFile();
        // parameter setup
        ParameterSet params = TestParams.of("format", "gpkg");
        ((TestParams) params).setFileUpload(dbFile);
        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init();
        // verify there are no nodes in the repository
        verifyNoCommitedNodes();

        ImportWebOp op = buildCommand(params);
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = result.getStatus();
        while (Status.RUNNING.equals(resultStatus) || Status.WAITING.equals(resultStatus)) {
            Thread.yield();
            resultStatus = result.getStatus();
        }
        Assert.assertEquals(Status.FINISHED, resultStatus);
        // verify the dbFile is gone
        verifyDbFileDeleted(dbFile);
        // verify data was imported
        verifyImport(Sets.newHashSet("Points", "Lines", "Polygons"));
    }

    @Test
    public void testImportTable() throws Throwable {
        final File dbFile = generateDbFile();
        // parameter setup
        ParameterSet params = TestParams.of("format", "gpkg", "layer", "Lines");
        ((TestParams) params).setFileUpload(dbFile);
        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init();
        // verify there are no nodes in the repository
        verifyNoCommitedNodes();

        ImportWebOp op = buildCommand(params);
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = result.getStatus();
        while (Status.RUNNING.equals(resultStatus) || Status.WAITING.equals(resultStatus)) {
            Thread.yield();
            resultStatus = result.getStatus();
        }
        Assert.assertEquals(Status.FINISHED, resultStatus);
        // verify the dbFile is gone
        verifyDbFileDeleted(dbFile);
        // verify data was imported
        verifyImport(Sets.newHashSet("Lines"));
    }

    @Test
    public void testImportTableWithDest() throws Throwable {
        final File dbFile = generateDbFile();
        // parameter setup
        ParameterSet params = TestParams.of("format", "gpkg", "layer", "Lines", "dest", "newLines");
        ((TestParams) params).setFileUpload(dbFile);
        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init();
        // verify there are no nodes in the repository
        verifyNoCommitedNodes();

        ImportWebOp op = buildCommand(params);
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = result.getStatus();
        while (Status.RUNNING.equals(resultStatus) || Status.WAITING.equals(resultStatus)) {
            Thread.yield();
            resultStatus = result.getStatus();
        }
        Assert.assertEquals(Status.FINISHED, resultStatus);
        // verify the dbFile is gone
        verifyDbFileDeleted(dbFile);
        // verify data was imported
        verifyImport(Sets.newHashSet("newLines"));
    }

    @Test
    public void testImportTableWithDestDuplicate() throws Throwable {
        final File dbFile = generateDbFile();
        // need to make a copy because the file will get deleted after import
        final File dbFileCopy = File.createTempFile("geogig-test-clone", ".gpkg");
        Files.copy(dbFile, dbFileCopy);
        // parameter setup
        ParameterSet params = TestParams.of("format", "gpkg", "layer", "Lines", "dest", "newLines");
        ((TestParams) params).setFileUpload(dbFile);
        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init();
        // verify there are no nodes in the repository
        verifyNoCommitedNodes();

        ImportWebOp op = buildCommand(params);
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = result.getStatus();
        while (Status.RUNNING.equals(resultStatus) || Status.WAITING.equals(resultStatus)) {
            Thread.yield();
            resultStatus = result.getStatus();
        }
        Assert.assertEquals(Status.FINISHED, resultStatus);
        // verify the dbFile is gone
        verifyDbFileDeleted(dbFile);
        // verify data was imported
        verifyImport(Sets.newHashSet("newLines"));
        // do it again but with a different dest
        params = TestParams.of("format", "gpkg", "layer", "Lines", "dest", "newLines2");
        // set the DB file to the copy since the original should be gone
        ((TestParams) params).setFileUpload(dbFileCopy);
        op = buildCommand(params);
        op.asyncContext = testAsyncContext;
        // runing a second time, specify the expected task Id
        result = run(op, "2");
        Assert.assertNotNull(result.getStatus());
        resultStatus = result.getStatus();
        while (Status.RUNNING.equals(resultStatus) || Status.WAITING.equals(resultStatus)) {
            Thread.yield();
            resultStatus = result.getStatus();
        }
        Assert.assertEquals(Status.FINISHED, resultStatus);
        // verify the dbFileCopy is gone
        verifyDbFileDeleted(dbFileCopy);
        // verify data was imported
        verifyImport(Sets.newHashSet("newLines", "newLines2"));
    }

    @Test
    public void testImportAllWithManualTransaction() throws Throwable {
        // get a DB file to import
        final File dbFile = generateDbFile();
        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init();
        // verify there are no nodes in the repository
        verifyNoCommitedNodes();

        // Begin a Transaction manually
        CommandBuilder.build("beginTransaction", new TestParams()).run(context);
        String expected = "{'response':{'success':true,'Transaction':{}}}";
        JSONObject beginResponse = getJSONResponse();
        JSONAssert.assertEquals(expected, beginResponse.toString(), false);
        JSONObject txnResponse = beginResponse.getJSONObject("response")
            .getJSONObject("Transaction");
        Assert.assertTrue("Expected ID string in Transaction Response", txnResponse.has("ID"));
        String txnId = txnResponse.getString("ID");

        // now call import with the manual transaction
        ParameterSet params = TestParams.of("format", "gpkg", "transactionId", txnId);
        ((TestParams) params).setFileUpload(dbFile);
        ImportWebOp op = buildCommand(params);
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = result.getStatus();
        while (Status.RUNNING.equals(resultStatus) || Status.WAITING.equals(resultStatus)) {
            Thread.yield();
            resultStatus = result.getStatus();
        }
        Assert.assertEquals(Status.FINISHED, resultStatus);
        // verify the dbFile is gone
        verifyDbFileDeleted(dbFile);
        // verify import hasn't been committed yet
        verifyNoCommitedNodes();
        // end the transaction and verify the import
        CommandBuilder.build("endTransaction", TestParams.of("transactionId", txnId)).run(context);
        JSONObject endResponse = getJSONResponse();
        expected = "{'response':{'success':true,'Transaction':''}}";
        JSONAssert.assertEquals(expected, endResponse.toString(), true);
        // verify data was imported
        verifyImport(Sets.newHashSet("Points", "Lines", "Polygons"));
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
            new Function<NodeRef, String>() {
            @Override
            public String apply(NodeRef input) {
                return input.name();
            }
        });
        for (String layerName : layerNames) {
            Assert.assertTrue("Expected layer \"" + layerName + "\" to exist in repo", nodeList
                .contains(layerName));
        }
    }

    private void verifyNoCommitedNodes() {
        Iterator<NodeRef> nodeIterator = context.getGeoGIG().command(LsTreeOp.class).call();
        Assert.assertFalse("Expected repo to be empty, but has nodes",
            nodeIterator.hasNext());
    }

    private AsyncContext.AsyncCommand<?> run(ImportWebOp op) throws JSONException,
        InterruptedException, ExecutionException {
        return run(op, "1");
    }

    private AsyncContext.AsyncCommand<?> run(ImportWebOp op, String taskId) throws JSONException,
        InterruptedException, ExecutionException {
        op.run(context);
        JSONObject response = getJSONResponse();
        JSONAssert.assertEquals(String.format(
            "{'task':{'id':%s,'description':'Importing Geopkg database file.','href':'/geogig/tasks/%s.json'}}",
            taskId, taskId),
            response.toString(), false);
        Optional<AsyncContext.AsyncCommand<?>> asyncCommand = Optional.absent();
        while (!asyncCommand.isPresent()) {
            asyncCommand = testAsyncContext.getAndPruneIfFinished(taskId);
        }
        Assert.assertNotNull(asyncCommand);
        Assert.assertNotNull(asyncCommand.get());
        return asyncCommand.get();
    }

    private File generateDbFile() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData();

        GeoPkgDataStoreFactory f = new GeoPkgDataStoreFactory();
        File dbFile = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
        dbFile.deleteOnExit();
        JDBCDataStore dataStore;
        final HashMap<String, Serializable> params = new HashMap<>(3);
        params.put(GeoPkgDataStoreFactory.DBTYPE.key, "geopkg");
        params.put(GeoPkgDataStoreFactory.DATABASE.key, dbFile.getAbsolutePath());
        params.put(GeoPkgDataStoreFactory.USER.key, "user");
        try {
            dataStore = f.createDataStore(params);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create GeoPkgDataStore", ioe);
        }
        GeopkgDataStoreExportOp op = repo.command(GeopkgDataStoreExportOp.class);
        op.setTarget(new Supplier<DataStore>() {
            @Override
            public DataStore get() {
                return dataStore;
            }

        });
        op.call();
        // now remove everything from the repo so we can verify import works
        RemoveOp rmOp = repo.command(RemoveOp.class).setRecursive(true)
            .addPathToRemove(pointsType.getTypeName())
            .addPathToRemove(linesType.getTypeName())
            .addPathToRemove(polysType.getTypeName());
        rmOp.call();
        // commit
        CommitOp commitOp = repo.command(CommitOp.class).setAll(true)
            .setMessage("removing features").setAuthor("Import Test", "importTest@example.com");
        commitOp.call();
        // verify status is good
        StatusSummary status = repo.command(StatusOp.class).call();
        Assert.assertTrue("Staged Count should be 0, but is " + status.getCountStaged(),
            status.getCountStaged() == 0);
        Assert.assertTrue("Conflict Count should be 0, but is " + status.getCountConflicts(),
            status.getCountConflicts() == 0);
        Assert.assertTrue("Unstaged Count should be 0, but is " + status.getCountUnstaged(),
            status.getCountUnstaged() == 0);
        // verify repo is empty
        verifyNoCommitedNodes();
        return dbFile;
    }
}
