/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geotools;

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
import static org.locationtech.geogig.web.api.TestData.pointsType;
import static org.locationtech.geogig.web.api.TestData.poly1;
import static org.locationtech.geogig.web.api.TestData.poly2;
import static org.locationtech.geogig.web.api.TestData.poly3;
import static org.locationtech.geogig.web.api.TestData.polysType;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.codehaus.jettison.json.JSONObject;
import org.geotools.data.DataStore;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.store.FeatureIteratorIterator;
import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.geotools.plumbing.DataStoreExportOp;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.geometry.BoundingBox;
import org.skyscreamer.jsonassert.JSONAssert;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ExportTest extends AbstractWebOpTest {

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
        return "export";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return Export.class;
    }

    @Test
    public void testExportDefaults() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData();

        Export op = buildCommand(TestParams.of());
        op.asyncContext = testAsyncContext;
        op.setOutputFormat(new TestOutputFormat());

        MemoryDataStore result = run(op);
        try {
            assertFeatures(result, pointsType.getTypeName(), point1, point2, point3);
            assertFeatures(result, linesType.getTypeName(), line1, line2, line3);
            assertFeatures(result, polysType.getTypeName(), poly1, poly2, poly3);
        } finally {
            result.dispose();
        }
    }

    @Test
    public void testExportBranch() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        // HEAD is at branch1
        testData.init().loadDefaultData().checkout("branch1");

        // but we request branch2
        Export op = buildCommand("root", "branch2");
        op.asyncContext = testAsyncContext;
        op.setOutputFormat(new TestOutputFormat());

        MemoryDataStore result = run(op);

        try {
            assertFeatures(result, pointsType.getTypeName(), point1, point3);
            assertFeatures(result, linesType.getTypeName(), line1, line3);
            assertFeatures(result, polysType.getTypeName(), poly1, poly3);
        } finally {
            result.dispose();
        }
    }

    @Test
    public void testExportLayernameFilter() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        new TestData(repo).init().loadDefaultData();

        // but we request branch2
        String layerFilter = linesType.getTypeName() + "," + polysType.getTypeName();
        Export op = buildCommand("path", layerFilter);
        op.asyncContext = testAsyncContext;
        op.setOutputFormat(new TestOutputFormat());

        MemoryDataStore result = run(op);
        try {
            assertFeatures(result, linesType.getTypeName(), line1, line2, line3);
            assertFeatures(result, polysType.getTypeName(), poly1, poly2, poly3);

            Set<String> exportedTypeNames = Sets.newHashSet(result.getTypeNames());
            assertFalse(exportedTypeNames.contains(pointsType.getTypeName()));
        } finally {
            result.dispose();
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
        Export op = buildCommand("root", "branch2", "bbox", bboxStr);
        op.asyncContext = testAsyncContext;
        op.setOutputFormat(new TestOutputFormat());

        MemoryDataStore result = run(op);
        try {
            assertFeatures(result, pointsType.getTypeName(), point3);
            assertFeatures(result, linesType.getTypeName(), line3);
            assertFeatures(result, polysType.getTypeName(), poly3);
        } finally {
            result.dispose();
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
        Export op = buildCommand("root", "branch2", "bbox", bboxFilter, "path", layerFilter);
        op.asyncContext = testAsyncContext;
        op.setOutputFormat(new TestOutputFormat());

        MemoryDataStore result = run(op);
        try {
            assertFeatures(result, linesType.getTypeName(), line3);
            assertFeatures(result, polysType.getTypeName(), poly3);

            Set<String> exportedTypeNames = Sets.newHashSet(result.getTypeNames());
            assertFalse(exportedTypeNames.contains(pointsType.getTypeName()));
        } finally {
            result.dispose();
        }
    }

    private MemoryDataStore run(Export op) throws JSONException, InterruptedException,
            ExecutionException {

        op.run(context);

        //final String expected = "{'task':{'id':1,'status':'RUNNING','description':'MemoryDataStore test output format','href':'/geogig/tasks/1.json'}}";
        final String expected = "{'task':{'id':1,'description':'MemoryDataStore test output format','href':'/geogig/tasks/1.json'}}";
        JSONObject response = getJSONResponse();
        JSONAssert.assertEquals(expected, response.toString(), false);

        Optional<AsyncCommand<?>> asyncCommand = Optional.absent();
        while (!asyncCommand.isPresent()) {
            asyncCommand = testAsyncContext.getAndPruneIfFinished("1");
        }

        MemoryDataStore result = (MemoryDataStore) asyncCommand.get().get();
        assertNotNull(result);
        return result;
    }

    private void assertFeatures(DataStore store, String typeName, SimpleFeature... expected)
            throws IOException {
        SimpleFeatureSource source = store.getFeatureSource(typeName);
        SimpleFeatureCollection features = source.getFeatures();

        Map<String, SimpleFeature> expectedFeatures;
        {
            List<SimpleFeature> list = Lists.newArrayList(expected);
            expectedFeatures = Maps.uniqueIndex(list, (f) -> f.getID());
        }
        Map<String, SimpleFeature> actualFeatures;
        {
            try (SimpleFeatureIterator fiter = features.features()) {
                List<SimpleFeature> list = Lists
                        .newArrayList(new FeatureIteratorIterator<SimpleFeature>(fiter));
                actualFeatures = Maps.uniqueIndex(list, (f) -> f.getID());
            }
        }

        assertEquals(expectedFeatures.keySet(), actualFeatures.keySet());
        assertEquals(expectedFeatures, actualFeatures);
    }

    static class TestOutputFormat extends Export.OutputFormat {

        private Supplier<DataStore> ds;

        public TestOutputFormat() {
            this.ds = Suppliers.ofInstance(TestData.newMemoryDataStore());
        }

        @Override
        public String getCommandDescription() {
            return "MemoryDataStore test output format";
        }

        @Override
        public Supplier<DataStore> getDataStore() {
            return ds;
        }

        @Override
        public DataStoreExportOp<MemoryDataStore> createCommand(CommandContext context) {
            return context.getGeoGIG().command(TestOutputFormat.Command.class);
        }

        static class Command extends DataStoreExportOp<MemoryDataStore> {

            @Override
            protected MemoryDataStore buildResult(DataStore targetStore) {
                return (MemoryDataStore) targetStore;
            }

        }
    }
}
