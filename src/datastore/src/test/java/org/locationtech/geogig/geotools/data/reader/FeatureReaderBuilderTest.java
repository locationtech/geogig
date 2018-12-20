/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.FeatureReader;
import org.geotools.data.Query;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.Hints;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.renderer.ScreenMap;
import org.junit.After;
import org.junit.Test;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.porcelain.index.CreateQuadTree;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.Mockito;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.PropertyIsEqualTo;
import org.opengis.filter.PropertyIsNotEqualTo;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.sort.SortBy;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class FeatureReaderBuilderTest extends RepositoryTestCase {

    // spy'ed builder
    private FeatureReaderBuilder builder;

    // spy'ed context given to the builder
    private Context context;

    // reader resulting from calling builder.build()
    private FeatureReader<SimpleFeatureType, SimpleFeature> reader;

    // spy'ed DiffTree command
    private DiffTree difftree;

    /**
     * Validate mockito usage right after a test case so it doesn't reports bad usage on the next
     * test case which is misleading
     */
    @After
    public void validate() {
        validateMockitoUsage();
    }

    /**
     * Creates the spatial index on the {@code pp} property of the Points dataset, with the provided
     * extra attributes, if any
     */
    private Index createIndex(@Nullable String... extraAttributes) {
        List<String> extraAtts = null;
        if (extraAttributes != null) {
            extraAtts = Lists.newArrayList(extraAttributes);
        }
        Index index = repo.command(CreateQuadTree.class).setExtraAttributes(extraAtts)
                .setGeometryAttributeName("pp").setTreeRefSpec(pointsName).call();
        return index;
    }

    /**
     * Verifies the index was using by inspecting {@link DiffTree#setNewTree} was given the index
     * objectId
     */
    private void verifyUsesIndex(Index index) {
        ObjectId indexTreeId = index.indexTreeId();
        verify(difftree).setNewTree(eq(indexTreeId));
    }

    @Override
    protected void setUpInternal() throws Exception {
        insertAndAdd(points1, points2, points3);
        commit("inital");

        SimpleFeatureType fullSchema = pointsType;
        RevFeatureType nativeType = RevFeatureType.builder().type(fullSchema).build();
        Context actualContext = repo.context();
        context = spy(actualContext);

        difftree = spy(context.command(DiffTree.class));
        doReturn(difftree).when(context).command(eq(DiffTree.class));

        NodeRef typeRef = context.workingTree().getFeatureTypeTrees().get(0);

        FeatureReaderBuilder b = FeatureReaderBuilder.builder(context, nativeType, typeRef);
        builder = spy(b);
    }

    @Test
    public void testSimpleGetAllQuery() throws Exception {
        verifyFeatures(Query.ALL, points1, points2, points3);
    }

    @Test
    public void testSimpleGetAllQueryIndexed() throws Exception {
        Index index = createIndex();
        verifyFeatures(Query.ALL, points1, points2, points3);
        verifyUsesIndex(index);
    }

    @Test
    public void testHeadRef() throws Exception {
        String branchName = "mybranch";
        branch(branchName);
        checkout(branchName);
        assertTrue(delete(points1));
        add();
        commit("deleted points1");
        checkout("master");

        builder.headRef(branchName);
        verifyFeatures(Query.ALL, points2, points3);
    }

    @Test
    public void testRespectsGeometryFactory() throws Exception {
        checkRespectsGeometryFactory();
    }

    @Test
    public void testRespectsGeometryFactoryWhenIndexed() throws Exception {
        Index index = createIndex();
        checkRespectsGeometryFactory();
        verifyUsesIndex(index);
    }

    private void checkRespectsGeometryFactory() throws Exception {
        GeometryFactory gfac = new GeometryFactory();
        builder.geometryFactory(gfac);
        reader = builder.build();
        Map<FeatureId, SimpleFeature> features = verifyFeatures(reader, points1, points2, points3);

        for (SimpleFeature f : features.values()) {
            Geometry g = (Geometry) f.getDefaultGeometry();
            assertSame(gfac, g.getFactory());
        }
    }

    @Test
    public void testResultingSchemaFullSchema() {
        FeatureReader<SimpleFeatureType, SimpleFeature> reader = builder.build();
        assertEquals(pointsType, reader.getFeatureType());
    }

    @Test
    public void testResultingSchemaExplicitTargetSchema() throws Exception {
        // as per ContentFeatureSource, the full schema may be a subset to start with, or it may
        // just have the namespace changed
        NameImpl namespaceRename = new NameImpl("http://geogig.org/testNamespace", pointsName);
        NameImpl localNameRename = new NameImpl("PointsRenamed");
        NameImpl namespaceAndLocalNameRename = new NameImpl("http://geogig.org/testNamespace",
                "PointsRenamed");

        testResultingSchemaExplicitTargetSchema(namespaceRename);
        testResultingSchemaExplicitTargetSchema(localNameRename);
        testResultingSchemaExplicitTargetSchema(namespaceAndLocalNameRename);

        testResultingSchemaExplicitTargetSchema(namespaceRename, "sp", "ip");
        testResultingSchemaExplicitTargetSchema(localNameRename, "ip", "sp");
        testResultingSchemaExplicitTargetSchema(namespaceAndLocalNameRename, "pp", "sp");
    }

    public void testResultingSchemaExplicitTargetSchema(Name targetName,
            @Nullable String... propertySubset) throws Exception {

        SimpleFeatureType redefinedFullSchema;
        {
            SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
            builder.setName(targetName);
            if (propertySubset == null || propertySubset.length == 0) {
                builder.addAll(pointsType.getAttributeDescriptors());
            } else {
                for (String att : propertySubset) {
                    AttributeDescriptor descriptor = pointsType.getDescriptor(att);
                    Preconditions.checkArgument(descriptor != null);
                    builder.add(descriptor);
                }
            }
            redefinedFullSchema = builder.buildFeatureType();
        }

        builder.targetSchema(redefinedFullSchema);
        FeatureReader<SimpleFeatureType, SimpleFeature> reader = builder.build();
        assertEquals(redefinedFullSchema, reader.getFeatureType());
        SimpleFeature f = reader.next();
        assertNotNull(f);
        assertEquals(redefinedFullSchema, f.getType());
    }

    private void assertEquals(SimpleFeatureType expected, SimpleFeatureType actual) {
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getAttributeDescriptors(), actual.getAttributeDescriptors());
    }

    @Test
    public void testResultingSchemaEmptySchema() {
        FeatureReader<SimpleFeatureType, SimpleFeature> reader = getReader(Query.FIDS);
        assertEquals(0, reader.getFeatureType().getAttributeCount());
        assertTrue(reader instanceof FeatureReaderAdapter);
        assertEquals(3, Iterators.size(((FeatureReaderAdapter) reader).iterator));
    }

    @Test
    public void testResultingSchemaExplicitSubset() {
        Query query = new Query();
        query.setPropertyNames(Lists.newArrayList("ip"));

        FeatureReader<SimpleFeatureType, SimpleFeature> reader = getReader(query);

        assertEquals(1, reader.getFeatureType().getAttributeCount());
        assertEquals("ip", reader.getFeatureType().getDescriptor(0).getLocalName());
    }

    @Test
    public void testResultingSchemaIncludesFilterAttributes() {
        Query query = new Query();
        query.setPropertyNames(Lists.newArrayList("ip"));

        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        PropertyIsEqualTo filter = ff.equals(ff.property("sp"), ff.literal("something"));

        query.setFilter(filter);

        FeatureReader<SimpleFeatureType, SimpleFeature> reader = getReader(query);

        SimpleFeatureType resultType = reader.getFeatureType();
        List<String> resultatts = Lists.transform(resultType.getAttributeDescriptors(),
                (d) -> d.getLocalName());

        assertEquals(1, resultatts.size());
        assertTrue(resultatts.contains("ip"));
    }

    /**
     * For a bbox filter, we don't need to include the geometry property if not explicitly
     * requested. Bounds are obtained from each Node in the RevTree
     */
    @Test
    public void testResultingSchemaIncludesFilterAttributesBBOXOptimization() {
        Query query = new Query();
        query.setPropertyNames(Lists.newArrayList("ip", "sp"));

        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        Filter filter = ff.bbox("pp", -1, -1, 1, 1, "EPSG:4326");

        query.setFilter(filter);

        FeatureReader<SimpleFeatureType, SimpleFeature> reader = getReader(query);

        SimpleFeatureType resultType = reader.getFeatureType();
        List<String> resultatts = Lists.transform(resultType.getAttributeDescriptors(),
                (d) -> d.getLocalName());

        assertEquals(2, resultatts.size());
        assertTrue(resultatts.contains("ip"));
        assertTrue(resultatts.contains("sp"));
    }

    @Test
    public void testIgnoreIndex() {
        createIndex();
        builder.ignoreIndex().build();
        NodeRef typeRef = context.workingTree().getFeatureTypeTrees().get(0);
        ObjectId canonicalTreeId = typeRef.getObjectId();
        verify(difftree).setNewTree(eq(canonicalTreeId));
    }

    @Test
    public void testFiltersUsingIndexMaterializedAttributes_FilterFullySupported()
            throws Exception {
        Index index = createIndex("ip", "sp");
        Query query = new Query();
        query.setPropertyNames(Query.ALL_NAMES);
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        PropertyIsNotEqualTo filter = ff.notEqual(ff.property("sp"), ff.literal("StringProp1_1"));
        query.setFilter(filter);
        verifyFeatures(query, points2, points3);
        verifyUsesIndex(index);
    }

    @Test
    public void testFiltersUsingIndexMaterializedAttributes_FilterPartiallySupported()
            throws Exception {
        Index index = createIndex("ip");
        Query query = new Query();
        query.setPropertyNames(Query.ALL_NAMES);
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

        PropertyIsNotEqualTo unsupported = ff.notEqual(ff.property("sp"),
                ff.literal("StringProp1_1"));

        PropertyIsEqualTo supported = ff.equals(ff.property("ip"),
                ff.literal(Integer.valueOf(2000)));

        Filter filter = ff.and(unsupported, supported);

        PrePostFilterSplitter filterSplitter = new PrePostFilterSplitter()
                .extraAttributes(ImmutableSet.of("ip")).filter(filter).build();

        Predicate<Bounded> preFilter = PreFilter.forFilter(filterSplitter.getPreFilter());
        assertTrue(preFilter instanceof PreFilter);
        assertEquals(supported, ((PreFilter) preFilter).filter);

        query.setFilter(filter);
        verifyFeatures(query, points2);
        verifyUsesIndex(index);
    }

    public @Test void testDiffTreeIteratorIsClosedOnError() throws IOException {
        RuntimeException expected = new RuntimeException();
        AutoCloseableIterator<DiffEntry> mockIt = mock(AutoCloseableIterator.class);
        when(mockIt.hasNext()).thenReturn(true);
        when(mockIt.next()).thenThrow(expected);
        doReturn(mockIt).when(difftree).call();

        FeatureReader<SimpleFeatureType, SimpleFeature> featureReader = builder.build();
        assertNotNull(featureReader);
        try {
            featureReader.hasNext();
            featureReader.next();
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertTrue(true);
        }
        Mockito.verify(mockIt, times(1)).close();
    }

    public @Test void testDiffTreeIteratorIsClosedOnPrematureFeatureIteratorClose()
            throws IOException {

        AutoCloseableIterator<DiffEntry> mockIt = mock(AutoCloseableIterator.class);
        when(mockIt.hasNext()).thenReturn(true);
        when(mockIt.next()).thenReturn(mock(DiffEntry.class));
        doReturn(mockIt).when(difftree).call();

        FeatureReader<SimpleFeatureType, SimpleFeature> featureReader = builder.build();
        // close the feature reader before being fully consumed
        featureReader.close();

        Mockito.verify(mockIt, times(0)).hasNext();
        Mockito.verify(mockIt, times(0)).next();
        Mockito.verify(mockIt, times(1)).close();
    }

    private Map<FeatureId, SimpleFeature> verifyFeatures(Query query, Feature... expectedFeatures)
            throws Exception {

        getReader(query);

        return verifyFeatures(reader, expectedFeatures);
    }

    private FeatureReader<SimpleFeatureType, SimpleFeature> getReader(Query query) {
        GeometryFactory geometryFactory = (GeometryFactory) query.getHints()
                .get(Hints.JTS_GEOMETRY_FACTORY);
        Integer offset = query.getStartIndex();
        Integer limit = query.isMaxFeaturesUnlimited() ? null : query.getMaxFeatures();
        String[] propertyNames = query.getPropertyNames();
        ScreenMap screenMap = (ScreenMap) query.getHints().get(Hints.SCREENMAP);
        SortBy[] sortBy = query.getSortBy();

        reader = builder.filter(query.getFilter())//
                .geometryFactory(geometryFactory)//
                .offset(offset)//
                .limit(limit)//
                .propertyNames(propertyNames)//
                .screenMap(screenMap)//
                .sortBy(sortBy)//
                .build();
        return reader;
    }

    private Map<FeatureId, SimpleFeature> verifyFeatures(
            FeatureReader<SimpleFeatureType, SimpleFeature> actualFeatures,
            Feature... expectedFeatures) throws Exception {

        Map<FeatureId, Feature> expectedMap = Maps.uniqueIndex(Lists.newArrayList(expectedFeatures),
                (f) -> f.getIdentifier());

        Map<FeatureId, SimpleFeature> actualMap = new HashMap<>();
        while (reader.hasNext()) {
            SimpleFeature f = reader.next();
            actualMap.put(f.getIdentifier(), f);
        }

        reader.close();

        assertEquals(expectedMap.keySet(), actualMap.keySet());

        for (FeatureId id : expectedMap.keySet()) {
            SimpleFeature expected = (SimpleFeature) expectedMap.get(id);
            SimpleFeature actual = (SimpleFeature) actualMap.get(id);
            assertEquals(expected.getAttributes(), actual.getAttributes());
        }

        return actualMap;
    }
}
