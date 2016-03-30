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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

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
    public void testImportMissingTableName() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData();

        ImportWebOp op = buildCommand("format", "gpkg");
        op.asyncContext = testAsyncContext;
        ex.expect(CommandSpecException.class);
        ex.expectMessage(
            "Request must specify a table name (table=name) or ALL (all=true)");
        run(op);
    }

    @Test
    public void testImportWithBothTableNameAndAll() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData();

        ImportWebOp op = buildCommand("format", "gpkg", "table", "Table1", "all", "true");
        op.asyncContext = testAsyncContext;
        ex.expect(CommandSpecException.class);
        ex.expectMessage(
            "Request must specify a table name (table=name) or ALL (all=true)");
        run(op);
    }

    @Test
    public void testImportAllMissingFileUpload() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData();

        ImportWebOp op = buildCommand("format", "gpkg", "all", "true");
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = result.getStatus();
        while (Status.RUNNING.equals(resultStatus)) {
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

        ImportWebOp op = buildCommand("format", "gpkg", "all", "true", "mockFileUpload", "blah");
        op.asyncContext = testAsyncContext;

        AsyncContext.AsyncCommand<?> result = run(op);
        Assert.assertNotNull(result.getStatus());
        Status resultStatus = result.getStatus();
        while (Status.RUNNING.equals(resultStatus)) {
            Thread.yield();
            resultStatus = result.getStatus();
        }
        Assert.assertEquals(Status.FAILED, resultStatus);
    }

    @Test
    public void testImportAll() throws Throwable {
        // parameter setup
         ParameterSet params = TestParams.of("format", "gpkg", "all", "true");
        ((TestParams)params).setFileUpload(generateDbFile());
        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init();

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
        // verify data was imported
        verifyImport();
    }

    @Test
    public void testImportAllWithManualTransaction() throws Throwable {
        // get a DB file to import
        final File uploadFile = generateDbFile();
        // setup and empty repo
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init();

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
         ParameterSet params = TestParams.of("format", "gpkg", "all", "true",
             "transactionId", txnId);
        ((TestParams)params).setFileUpload(uploadFile);
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
        // verify import hasn't been committed yet
        verifyImportNotCommitted();
        // end the transaction and verify the import
        CommandBuilder.build("endTransaction", TestParams.of("transactionId", txnId)).run(context);
        JSONObject endResponse = getJSONResponse();
        expected = "{'response':{'success':true,'Transaction':''}}";
        JSONAssert.assertEquals(expected, endResponse.toString(), true);
        // verify data was imported
        verifyImport();
    }

    private void verifyImport() {
        // get the list
        Iterator<NodeRef> nodeIterator = context.getGeoGIG().command(LsTreeOp.class).call();
        Assert.assertTrue("Expected repo to have some nodes, but was empty",
            nodeIterator.hasNext());
        List<NodeRef> nodeList = Lists.newArrayList(nodeIterator);
        Assert.assertTrue("Expected repo to have 3 nodeRefs, but has " + nodeList.size(),
            3 == nodeList.size());
    }

    private void verifyImportNotCommitted() {
        Iterator<NodeRef> nodeIterator = context.getGeoGIG().command(LsTreeOp.class).call();
        Assert.assertFalse("Expected repo to still be empty, but has nodes",
            nodeIterator.hasNext());
    }

    private AsyncContext.AsyncCommand<?> run(ImportWebOp op) throws JSONException,
        InterruptedException, ExecutionException {
        op.run(context);
        JSONObject response = getJSONResponse();
        JSONAssert.assertEquals(
            "{'task':{'id':1,'description':'Importing Geopkg database file.','href':'/geogig/tasks/1.json'}}",
            response.toString(), false);
        Optional<AsyncContext.AsyncCommand<?>> asyncCommand = Optional.absent();
        while (!asyncCommand.isPresent()) {
            asyncCommand = testAsyncContext.getAndPruneIfFinished("1");
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
        // get a connection to initialize the DataStore, then safely close it
        Connection con;
        try {
            con = dataStore.getDataSource().getConnection();
        } catch (SQLException sqle) {
            throw new RuntimeException("Unable to get a connection to GeoPkgDataStore", sqle);
        }
        dataStore.closeSafe(con);
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
        Iterator<NodeRef> nodeList = repo.command(LsTreeOp.class).call();
        Assert.assertFalse("Expected empty node list, but nodes still exist", nodeList.hasNext());
        return dbFile;
    }
}
