/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rest.geopkg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.test.TestData.line2;
import static org.locationtech.geogig.test.TestData.line3;
import static org.locationtech.geogig.test.TestData.linesType;
import static org.locationtech.geogig.test.TestData.point1;
import static org.locationtech.geogig.test.TestData.point1_modified;
import static org.locationtech.geogig.test.TestData.point2;
import static org.locationtech.geogig.test.TestData.point3;
import static org.locationtech.geogig.test.TestData.pointsType;
import static org.locationtech.geogig.test.TestData.poly1;
import static org.locationtech.geogig.test.TestData.poly2;
import static org.locationtech.geogig.test.TestData.poly3;
import static org.locationtech.geogig.test.TestData.polysType;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.json.JsonObject;

import org.geotools.data.DataStore;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.geotools.geopkg.GeopkgGeogigMetadata;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.geotools.ExportDiff;
import org.locationtech.geogig.rest.repository.TestParams;
import org.locationtech.geogig.test.TestData;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.JsonUtils;
import org.locationtech.geogig.web.api.ParameterSet;
import org.opengis.feature.simple.SimpleFeature;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class GeoPackageExportDiffIntegrationTest extends AbstractWebOpTest {

    private CommandContext context;

    private AsyncContext testAsyncContext;

    @Before
    public void before() {
        context = super.testContext.get();
        testAsyncContext = AsyncContext.createNew();
        System.setProperty("gt2.jdbc.trace", "true");
    }

    @After
    public void after() {
        testAsyncContext.shutDown();
    }

    @Override
    protected ExportDiff buildCommand(ParameterSet params) {
        ExportDiff o = super.buildCommand(params);
        o.asyncContext = testAsyncContext;
        return o;
    }

    @Override
    protected String getRoute() {
        return "export-diff";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return ExportDiff.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testExportDiff() throws Exception {
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");
        testData.insert(TestData.point1, TestData.line1, TestData.poly1);
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("point1, line1, poly1")
                .call();
        testData.addAndCommit("modify point1; add point2, line2, poly2", TestData.point1_modified,
                TestData.point2, TestData.line2, TestData.poly2);
        testData.insert(TestData.point3, TestData.line3, TestData.poly3);
        testData.remove(TestData.poly1);
        testData.add();
        RevCommit commit3 = geogig.command(CommitOp.class)
                .setMessage("remove poly1; add point3, line3, poly3")
                .call();
        testData.checkout("master");

        ExportDiff op = buildCommand(TestParams.of("format", "gpkg", "oldRef",
                commit1.getId().toString(), "newRef", commit3.getId().toString()));

        File result = run(op);
        DataStore store = store(result);
        try {
            assertFeatures(store, pointsType.getTypeName(), point1_modified,
                    point2, point3);
            assertFeatures(store, linesType.getTypeName(), line2, line3);
            assertFeatures(store, polysType.getTypeName(), poly2, poly3);

            // Check _changes table to make sure all the changes are properly recorded
            Map<String, ChangeType> pointChanges = getChangesForTable(pointsType.getTypeName(), result);
            assertEquals(3, pointChanges.size());
            assertEquals(ChangeType.MODIFIED, pointChanges.get(point1_modified.getIdentifier().getID()));
            assertEquals(ChangeType.ADDED, pointChanges.get(point2.getIdentifier().getID()));
            assertEquals(ChangeType.ADDED, pointChanges.get(point3.getIdentifier().getID()));

            Map<String, ChangeType> lineChanges = getChangesForTable(linesType.getTypeName(),
                    result);
            assertEquals(2, lineChanges.size());
            assertEquals(ChangeType.ADDED, lineChanges.get(line2.getIdentifier().getID()));
            assertEquals(ChangeType.ADDED, lineChanges.get(line3.getIdentifier().getID()));

            Map<String, ChangeType> polyChanges = getChangesForTable(polysType.getTypeName(),
                    result);
            assertEquals(3, polyChanges.size());
            assertEquals(ChangeType.REMOVED, polyChanges.get(poly1.getIdentifier().getID()));
            assertEquals(ChangeType.ADDED, polyChanges.get(poly2.getIdentifier().getID()));
            assertEquals(ChangeType.ADDED, polyChanges.get(poly3.getIdentifier().getID()));
        } finally {
            store.dispose();
        }
    }

    @Test
    public void testExportDiffInverse() throws Exception {
        // Same as #testExportDiff, but reversing the diff so that commit1 is the newRef and commit3
        // is the oldRef
        Repository geogig = testContext.get().getRepository();
        TestData testData = new TestData(geogig);
        testData.init();
        testData.checkout("master");
        testData.insert(TestData.point1, TestData.line1, TestData.poly1);
        testData.add();
        RevCommit commit1 = geogig.command(CommitOp.class).setMessage("point1, line1, poly1")
                .call();
        testData.addAndCommit("modify point1; add point2, line2, poly2", TestData.point1_modified,
                TestData.point2, TestData.line2, TestData.poly2);
        testData.insert(TestData.point3, TestData.line3, TestData.poly3);
        testData.remove(TestData.poly1);
        testData.add();
        RevCommit commit3 = geogig.command(CommitOp.class)
                .setMessage("remove poly1; add point3, line3, poly3").call();
        testData.checkout("master");

        ExportDiff op = buildCommand(TestParams.of("format", "gpkg", "oldRef",
                commit3.getId().toString(), "newRef", commit1.getId().toString()));

        File result = run(op);
        DataStore store = store(result);
        try {
            assertFeatures(store, pointsType.getTypeName(), point1);
            assertFeatures(store, linesType.getTypeName());
            assertFeatures(store, polysType.getTypeName(), poly1);

            // Check _changes table to make sure all the changes are properly recorded
            Map<String, ChangeType> pointChanges = getChangesForTable(pointsType.getTypeName(),
                    result);
            assertEquals(3, pointChanges.size());
            assertEquals(ChangeType.MODIFIED,
                    pointChanges.get(point1.getIdentifier().getID()));
            assertEquals(ChangeType.REMOVED, pointChanges.get(point2.getIdentifier().getID()));
            assertEquals(ChangeType.REMOVED, pointChanges.get(point3.getIdentifier().getID()));

            Map<String, ChangeType> lineChanges = getChangesForTable(linesType.getTypeName(),
                    result);
            assertEquals(2, lineChanges.size());
            assertEquals(ChangeType.REMOVED, lineChanges.get(line2.getIdentifier().getID()));
            assertEquals(ChangeType.REMOVED, lineChanges.get(line3.getIdentifier().getID()));

            Map<String, ChangeType> polyChanges = getChangesForTable(polysType.getTypeName(),
                    result);
            assertEquals(3, polyChanges.size());
            assertEquals(ChangeType.ADDED, polyChanges.get(poly1.getIdentifier().getID()));
            assertEquals(ChangeType.REMOVED, polyChanges.get(poly2.getIdentifier().getID()));
            assertEquals(ChangeType.REMOVED, polyChanges.get(poly3.getIdentifier().getID()));
        } finally {
            store.dispose();
        }
    }

    private File run(ExportDiff op) throws InterruptedException, ExecutionException {

        op.run(context);

        final String expected = "{\"task\":{\"id\":1,\"description\":\"Export changes between two commits to Geopackage database\",\"href\":\"/geogig/tasks/1.json\"}}";

        JsonObject response = getJSONResponse();
        assertTrue(JsonUtils.jsonEquals(JsonUtils.toJSON(expected),
                JsonUtils.toJSON(response.toString()), false));

        Optional<AsyncCommand<?>> asyncCommand = Optional.absent();
        while (!asyncCommand.isPresent()) {
            Thread.yield();
            asyncCommand = testAsyncContext.getAndPruneIfFinished("1");
        }

        File result = (File) asyncCommand.get().get();
        assertNotNull(result);
        return result;
    }

    private DataStore store(File result) throws InterruptedException, ExecutionException {

        assertNotNull(result);

        final GeoPkgDataStoreFactory factory = new GeoPkgDataStoreFactory();

        final Map<String, Serializable> params = ImmutableMap.of(GeoPkgDataStoreFactory.DBTYPE.key,
                "geopkg", GeoPkgDataStoreFactory.DATABASE.key, result.getAbsolutePath());

        DataStore dataStore;
        try {
            dataStore = factory.createDataStore(params);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create GeoPkgDataStore", ioe);
        }
        if (null == dataStore) {
            throw new RuntimeException("Unable to create GeoPkgDataStore");
        }

        return dataStore;
    }

    private void assertFeatures(DataStore store, String typeName, SimpleFeature... expected)
            throws Exception {
        try (Connection connection = ((JDBCDataStore) store).getConnection(Transaction.AUTO_COMMIT);
                GeopkgGeogigMetadata metadata = new GeopkgGeogigMetadata(connection)) {
            Map<String, String> mappings = metadata.getFidMappings(typeName);

            SimpleFeatureSource source = store.getFeatureSource(typeName);
            SimpleFeatureCollection features = source.getFeatures();

            Map<String, SimpleFeature> expectedFeatures;
            {
                List<SimpleFeature> list = Lists.newArrayList(expected);
                expectedFeatures = Maps.uniqueIndex(list, (f) -> f.getID());
            }
            Set<String> actualFeatureIDs = new HashSet<String>();
            {
                try (SimpleFeatureIterator fiter = features.features()) {
                    while (fiter.hasNext()) {
                        SimpleFeature feature = fiter.next();
                        actualFeatureIDs.add(mappings.get(feature.getID().split("\\.")[1]));
                    }
                }
            }

            Set<String> expectedFeatureIDs = expectedFeatures.keySet();

            assertEquals(expectedFeatureIDs, actualFeatureIDs);
        }
    }

    private Map<String, ChangeType> getChangesForTable(String tableName, File gpkg)
            throws IOException, SQLException {
        Map<String, ChangeType> changes = new HashMap<String, ChangeType>();
        GeoPackage geoPackage = new GeoPackage(gpkg);
        try {
            try (Connection c = geoPackage.getDataSource().getConnection()) {
                try (Statement st = c.createStatement()) {
                    String changeTable = tableName + "_changes";
                    try (ResultSet rs = st.executeQuery("select * from " + changeTable)) {
                        while (rs.next()) {
                            String fid = rs.getString("geogig_fid");
                            int audit_op = rs.getInt("audit_op");
                            changes.put(fid, changeTypeForAuditOp(audit_op));
                        }
                    }
                }
            }
        } finally {
            geoPackage.close();
        }
        return changes;
    }

    private ChangeType changeTypeForAuditOp(int audit_op) {
        ChangeType change = null;
        switch (audit_op) {
        case GeopkgGeogigMetadata.AUDIT_OP_INSERT:
            change = ChangeType.ADDED;
            break;
        case GeopkgGeogigMetadata.AUDIT_OP_UPDATE:
            change = ChangeType.MODIFIED;
            break;
        case GeopkgGeogigMetadata.AUDIT_OP_DELETE:
            change = ChangeType.REMOVED;
            break;
        }
        return change;
    }

}
