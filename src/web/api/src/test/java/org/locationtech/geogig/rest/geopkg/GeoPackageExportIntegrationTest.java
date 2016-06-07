/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geopkg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.locationtech.geogig.web.api.TestData.line1;
import static org.locationtech.geogig.web.api.TestData.line2;
import static org.locationtech.geogig.web.api.TestData.line3;
import static org.locationtech.geogig.web.api.TestData.linesType;
import static org.locationtech.geogig.web.api.TestData.point1;
import static org.locationtech.geogig.web.api.TestData.point2;
import static org.locationtech.geogig.web.api.TestData.point3;
import static org.locationtech.geogig.web.api.TestData.point_string_fid;
import static org.locationtech.geogig.web.api.TestData.pointsType;
import static org.locationtech.geogig.web.api.TestData.poly1;
import static org.locationtech.geogig.web.api.TestData.poly2;
import static org.locationtech.geogig.web.api.TestData.poly3;
import static org.locationtech.geogig.web.api.TestData.polysType;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.codehaus.jettison.json.JSONObject;
import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStore;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.geotools.geopkg.GeopkgGeogigMetadata;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.geotools.Export;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.TestData;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.geometry.BoundingBox;
import org.skyscreamer.jsonassert.JSONAssert;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class GeoPackageExportIntegrationTest extends AbstractWebOpTest {

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
    protected Export buildCommand(@Nullable String... optionsKvp) {
        Export o = super.buildCommand(optionsKvp);
        o.asyncContext = testAsyncContext;
        return o;
    }

    @Override
    protected String getRoute() {
        return "export";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Export.class;
    }

    @Override
    protected boolean requiresTransaction() {
        return false;
    }

    @Test
    public void testExportDefaults() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData();
        testData.addAndCommit("Added feature with non-numeric fid.", point_string_fid);

        Export op = buildCommand("format", "gpkg");

        File result = run(op);
        DataStore store = store(result);
        try {
            assertFeatures(store, pointsType.getTypeName(), point_string_fid, point1, point2,
                    point3);
            assertFeatures(store, linesType.getTypeName(), line1, line2, line3);
            assertFeatures(store, polysType.getTypeName(), poly1, poly2, poly3);
        } finally {
            store.dispose();
        }
    }

    @Test
    public void testExportDefaultsIntechangeExtension() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData();

        Export op = buildCommand("format", "gpkg", "interchange", "true");

        File result = run(op);
        DataStore store = store(result);
        try {
            assertFeatures(store, pointsType.getTypeName(), point1, point2, point3);
            assertFeatures(store, linesType.getTypeName(), line1, line2, line3);
            assertFeatures(store, polysType.getTypeName(), poly1, poly2, poly3);
        } finally {
            store.dispose();
        }

        assertEquals(Sets.newHashSet("Points_audit", "Lines_audit", "Polygons_audit"),
                getAuditTableNames(result));
    }

    @Test
    public void testExportBranch() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        // HEAD is at branch1
        testData.init().loadDefaultData().checkout("branch1");

        // but we request branch2
        Export op = buildCommand("format", "GPKG", "root", "branch2");

        File result = run(op);
        DataStore store = store(result);
        try {
            assertFeatures(store, pointsType.getTypeName(), point1, point3);
            assertFeatures(store, linesType.getTypeName(), line1, line3);
            assertFeatures(store, polysType.getTypeName(), poly1, poly3);
        } finally {
            store.dispose();
        }
    }

    @Test
    public void testExportLayernameFilter() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        new TestData(repo).init().loadDefaultData();

        // but we request branch2
        String layerFilter = linesType.getTypeName() + "," + polysType.getTypeName();
        Export op = buildCommand("format", "gpkg", "path", layerFilter);

        File result = run(op);
        DataStore store = store(result);
        try {
            assertFeatures(store, linesType.getTypeName(), line1, line2, line3);
            assertFeatures(store, polysType.getTypeName(), poly1, poly2, poly3);

            Set<String> exportedTypeNames = Sets.newHashSet(store.getTypeNames());
            assertFalse(exportedTypeNames.contains(pointsType.getTypeName()));
        } finally {
            store.dispose();
        }
    }

    @Test
    public void testExportBranchBBoxFilter() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        // HEAD is at branch1
        testData.init().loadDefaultData().checkout("branch1");

        BoundingBox bounds = point3.getDefaultGeometryProperty().getBounds();
        String bboxStr = String.format("%f,%f,%f,%f,EPSG:4326", bounds.getMinX(), bounds.getMinY(),
                bounds.getMaxX(), bounds.getMaxY());
        // but we request branch2
        Export op = buildCommand("format", "gpkg", "root", "branch2", "bbox", bboxStr);

        File result = run(op);
        DataStore store = store(result);
        try {
            assertFeatures(store, pointsType.getTypeName(), point3);
            assertFeatures(store, linesType.getTypeName(), line3);
            assertFeatures(store, polysType.getTypeName(), poly3);
        } finally {
            store.dispose();
        }
    }

    @Test
    public void testExportBranchBBoxAndLayerFilter() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        // HEAD is at branch1
        testData.init().loadDefaultData().checkout("branch1");

        BoundingBox bounds = point3.getDefaultGeometryProperty().getBounds();
        String bboxFilter = String.format("%f,%f,%f,%f,EPSG:4326", bounds.getMinX(),
                bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
        String layerFilter = linesType.getTypeName() + "," + polysType.getTypeName();
        // but we request branch2
        Export op = buildCommand("format", "gpkg", "root", "branch2", "bbox", bboxFilter,
                "path", layerFilter);

        File result = run(op);
        DataStore store = store(result);
        try {
            assertFeatures(store, linesType.getTypeName(), line3);
            assertFeatures(store, polysType.getTypeName(), poly3);

            Set<String> exportedTypeNames = Sets.newHashSet(store.getTypeNames());
            assertFalse(exportedTypeNames.contains(pointsType.getTypeName()));
        } finally {
            store.dispose();
        }
    }

    @Test
    public void testExportBranchBBoxAndLayerFilterInterchangeExtension() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        // HEAD is at branch1
        testData.init().loadDefaultData().checkout("branch1");

        BoundingBox bounds = point3.getDefaultGeometryProperty().getBounds();
        String bboxFilter = String.format("%f,%f,%f,%f,EPSG:4326", bounds.getMinX(),
                bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
        String layerFilter = linesType.getTypeName() + "," + polysType.getTypeName();
        // but we request branch2
        Export op = buildCommand("format", "gpkg", "root", "branch2", "bbox", bboxFilter,
                "path", layerFilter, "interchange", "true");

        final File result = run(op);
        final DataStore store = store(result);
        try {
            assertFeatures(store, linesType.getTypeName(), line3);
            assertFeatures(store, polysType.getTypeName(), poly3);

            Set<String> exportedTypeNames = Sets.newHashSet(store.getTypeNames());
            assertFalse(exportedTypeNames.contains(pointsType.getTypeName()));
        } finally {
            store.dispose();
        }

        assertEquals(Sets.newHashSet("Lines_audit", "Polygons_audit"), getAuditTableNames(result));
    }

    private File run(Export op) throws JSONException, InterruptedException, ExecutionException {

        op.run(context);

        final String expected;
        if (Boolean.parseBoolean(op.options.getFirstValue("interchange"))) {
            expected = "{'task':{'id':1,'description':'Export to Geopackage database with geogig interchange format extension','href':'/geogig/tasks/1.json'}}";
        } else {
            expected = "{'task':{'id':1,'description':'Export to Geopackage database','href':'/geogig/tasks/1.json'}}";
        }

        JSONObject response = getJSONResponse();
        JSONAssert.assertEquals(expected, response.toString(), false);

        Optional<AsyncCommand<?>> asyncCommand = Optional.absent();
        while (!asyncCommand.isPresent()) {
            Thread.yield();
            asyncCommand = testAsyncContext.getAndPruneIfFinished("1");
        }

        File result = (File) asyncCommand.get().get();
        assertNotNull(result);
        return result;
    }

    private DataStore store(File result)
            throws JSONException, InterruptedException,
            ExecutionException {

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

    private void assertFeatures(DataStore store, String typeName,
            SimpleFeature... expected)
            throws Exception {
        Connection connection = ((JDBCDataStore) store).getConnection(Transaction.AUTO_COMMIT);
        GeopkgGeogigMetadata metadata = new GeopkgGeogigMetadata(connection);
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

    private Set<String> getAuditTableNames(File gpkg) throws IOException, SQLException {
        GeoPackage geoPackage = new GeoPackage(gpkg);
        Set<String> auditTables = new HashSet<>();
        try {
            try (Connection c = geoPackage.getDataSource().getConnection()) {
                try (Statement st = c.createStatement()) {
                    // CREATE TABLE geogig_audited_tables (table_name VARCHAR, mapped_path VARCHAR,
                    // audit_table VARCHAR, root_tree_id VARCHAR);
                    try (ResultSet rs = st.executeQuery("select * from geogig_audited_tables")) {
                        while (rs.next()) {
                            String table = rs.getString("table_name");
                            String treePath = rs.getString("mapped_path");
                            String auditTable = rs.getString("audit_table");
                            String rootTreeId = rs.getString("commit_id");
                            auditTables.add(auditTable);
                        }
                    }
                }
            }
        } finally {
            geoPackage.close();
        }
        return auditTables;
    }

}
