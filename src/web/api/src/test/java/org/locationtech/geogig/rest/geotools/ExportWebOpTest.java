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
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.geotools.data.DataStore;
import org.geotools.data.Query;
import org.geotools.data.QueryCapabilities;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.memory.MemoryFeatureStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.data.store.FeatureIteratorIterator;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.geotools.plumbing.DataStoreExportOp;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.AbstractWebOpTest;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.TestData;
import org.locationtech.geogig.web.api.TestParams;
import org.opengis.feature.simple.SimpleFeature;
import org.skyscreamer.jsonassert.JSONAssert;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class ExportWebOpTest extends AbstractWebOpTest {

    private CommandContext context;

    @Before
    public void before() {
        context = super.testContext.get();
    }

    @Override
    protected String getRoute() {
        return "export";
    }

    @Override
    protected Class<? extends AbstractWebAPICommand> getCommandClass() {
        return ExportWebOp.class;
    }

    @Test
    public void testExportDefaults() throws Exception {
        GeoGIG repo = context.getGeoGIG();
        TestData testData = new TestData(repo);
        testData.init().loadDefaultData().checkout("branch1");

        ParameterSet options = TestParams.of();
        ExportWebOp op = buildCommand(options);

        ExportWebOp.OutputFormat testFormat = new TestOutputFormat();
        op.setOutputFormat(testFormat);

        op.run(context);

        final String expected = "{'task':{'id':1,'status':'RUNNING','description':'MemoryDataStore test output format','href':'/geogig/tasks/1.json'}}";
        JSONObject response = getJSONResponse();
        // System.err.println(response);
        JSONAssert.assertEquals(expected, response.toString(), false);

        AsyncContext asyncContext = AsyncContext.get();
        Optional<AsyncCommand<?>> asyncCommand = Optional.absent();
        while (!asyncCommand.isPresent()) {
            asyncCommand = asyncContext.getAndPruneIfFinished("1");
        }

        MemoryDataStore result = (MemoryDataStore) asyncCommand.get().get();
        assertNotNull(result);

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
            List<SimpleFeature> list = Lists
                    .newArrayList(new FeatureIteratorIterator<SimpleFeature>(features.features()));
            actualFeatures = Maps.uniqueIndex(list, (f) -> f.getID());
        }

        assertEquals(expectedFeatures.keySet(), actualFeatures.keySet());
        assertEquals(expectedFeatures, actualFeatures);
    }

    private static class TestOutputFormat extends ExportWebOp.OutputFormat {

        private Supplier<DataStore> ds;

        public TestOutputFormat() {
            this.ds = Suppliers.ofInstance(new MemoryDataStore() {

                @Override
                protected ContentFeatureSource createFeatureSource(ContentEntry entry, Query query) {
                    return new MemoryFeatureStore(entry, query) {
                        @Override
                        protected QueryCapabilities buildQueryCapabilities() {
                            return new QueryCapabilities() {
                                @Override
                                public boolean isUseProvidedFIDSupported() {
                                    return true;
                                }

                            };
                        };

                    };
                }

            });
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
