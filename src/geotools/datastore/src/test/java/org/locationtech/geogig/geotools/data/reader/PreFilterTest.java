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

import static org.geotools.filter.text.ecql.ECQL.toFilter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.hashString;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.JTS;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.opengis.filter.BinaryComparisonOperator;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.PropertyIsGreaterThan;
import org.opengis.filter.PropertyIsGreaterThanOrEqualTo;
import org.opengis.filter.PropertyIsLessThan;
import org.opengis.filter.PropertyIsLessThanOrEqualTo;
import org.opengis.filter.PropertyIsLike;
import org.opengis.filter.PropertyIsNil;
import org.opengis.filter.PropertyIsNotEqualTo;
import org.opengis.filter.PropertyIsNull;
import org.opengis.filter.spatial.BBOX;
import org.opengis.filter.spatial.Beyond;
import org.opengis.filter.spatial.Contains;
import org.opengis.filter.spatial.Crosses;
import org.opengis.filter.spatial.DWithin;
import org.opengis.filter.spatial.Disjoint;
import org.opengis.filter.spatial.Equals;
import org.opengis.filter.spatial.Intersects;
import org.opengis.filter.spatial.Overlaps;
import org.opengis.filter.spatial.Touches;
import org.opengis.filter.spatial.Within;
import org.opengis.filter.temporal.After;
import org.opengis.filter.temporal.AnyInteracts;

import com.google.common.collect.ImmutableMap;

public class PreFilterTest {

    private final Date DATE_VALUE = new Date(1486344231314L);

    private final FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

    private Node testNode;

    private NodeRef testNodeRef;

    private Bucket testBucket;

    private PrePostFilterSplitter filterSplitter;

    @Before
    public void before() {
        ObjectId oid = hashString("id");
        ObjectId metadataId = hashString("metadata");
        Envelope bounds = new Envelope(0, 180, 0, 90);

        Map<String, Object> materializedAttributes = new HashMap<>();
        materializedAttributes.put("int", 1);
        materializedAttributes.put("double", 0.5);
        materializedAttributes.put("date", DATE_VALUE);
        materializedAttributes.put("string", "geogig");
        materializedAttributes.put("nullprop", null);

        filterSplitter = new PrePostFilterSplitter()
                .extraAttributes(materializedAttributes.keySet());

        Map<String, Object> extraData = ImmutableMap.of(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA,
                materializedAttributes);

        testNode = RevObjectFactory.defaultInstance().createNode("testFid", oid, metadataId,
                TYPE.FEATURE, bounds, extraData);

        testNodeRef = new NodeRef(testNode, "fakeLayerName", metadataId);

        ObjectId bucketId = hashString("bucketId");
        testBucket = RevObjectFactory.defaultInstance().createBucket(bucketId, 0, bounds);

    }

    private PreFilter preFilter(Filter filter) {
        filterSplitter.filter(filter).build();
        Filter preFilter = filterSplitter.getPreFilter();
        return PreFilter.forFilter(preFilter);
    }

    @Test
    public void excludeFilter() {
        PreFilter predicate = preFilter(Filter.EXCLUDE);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        assertFalse(predicate.apply(testBucket));
        assertFalse(predicate.apply(null));
    }

    @Test
    public void includeFilter() {
        PreFilter predicate = preFilter(Filter.INCLUDE);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));
        assertTrue(predicate.apply(null));
    }

    @Test
    public void andFilter() throws Exception {
        Filter filter = toFilter("int = 1 AND string = 'geogig'");
        PreFilter predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));
        assertFalse(predicate.apply(null));

        filter = toFilter("int = 2 AND string = 'geogig'");
        predicate = preFilter(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // when given a bucket, it just must evaluate to true for PreorderDiffWalk to continue
        // traversal
        assertTrue(predicate.apply(testBucket));

        filter = toFilter("int = 2 AND string = 'geogig' AND nonExistent = 'something'");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
    }

    @Test
    public void orFilter() throws Exception {
        Filter filter = toFilter("int = 0 OR string = 'geogig'");
        PreFilter predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = toFilter("int = 2 OR string = 'something else'");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // when given a bucket, it just must evaluate to true for PreorderDiffWalk to continue
        // traversal
        assertTrue(predicate.apply(testBucket));

        filter = toFilter("int = 2 OR string = 'something else' OR nonmat ='xyz'");
        predicate = preFilter(filter);
        assertTrue(isAcceptEverything(predicate)); // cannot be optimized

    }

    @Test
    public void idFilter() throws Exception {
        Filter filter = toFilter("IN ('fake1', 'testFid', 'fake2')");
        PreFilter predicate = preFilter(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = toFilter("IN ('fake1', 'fake2', 'fake3')");
        predicate = preFilter(filter);

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // when given a bucket, it just must evaluate to true for PreorderDiffWalk to continue
        // traversal
        assertTrue(predicate.apply(testBucket));
    }

    @Test
    public void notFilter() throws Exception {
        Filter filter = toFilter("NOT(int = 1)");
        PreFilter predicate = preFilter(filter);

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = toFilter("NOT(double < 0)");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        // when given a bucket, it just must evaluate to true for PreorderDiffWalk to continue
        // traversal
        assertTrue(predicate.apply(testBucket));
    }

    private void testBinaryComparisonOperator(String cql, boolean expected) throws Exception {
        BinaryComparisonOperator filter = (BinaryComparisonOperator) toFilter(cql);
        PreFilter predicate = preFilter(filter);
        if (expected) {
            assertTrue(predicate.apply(testNode));
        } else {
            assertFalse(predicate.apply(testNode));
        }
    }

    @Test
    public void testBinaryComparisonOperators() throws Exception {
        testBinaryComparisonOperator("double > 0", true);
        testBinaryComparisonOperator("0 < double", true);
        testBinaryComparisonOperator("double < nonMaterialized", true);
    }

    @Test
    public void propertyIsBetweenFilter() throws Exception {
        Filter filter = toFilter("double between 0.1 and 0.6");
        PreFilter predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = toFilter("double between 0.1 and 0.49999");
        predicate = preFilter(filter);

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // when given a bucket, it just must evaluate to true for PreorderDiffWalk to continue
        // traversal
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = toFilter("nonMaterializedProperty between 0.1 and 0.5");
        predicate = preFilter(filter);

        assertTrue(isAcceptEverything(predicate));

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsEqualToFilter() throws Exception {
        PreFilter predicate = preFilter(toFilter("double = 0.5"));

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        predicate = preFilter(toFilter("int = 1.0"));

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        Filter filter = toFilter("nonMaterializedProperty = 1");
        predicate = preFilter(filter);

        assertTrue(isAcceptEverything(predicate));

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsNotEqualToFilter() throws Exception {
        // ECQL.toFilter("double <> 0.1") returns a NOT filter instead of a PropertyIsNotEqualTo
        PropertyIsNotEqualTo filter = ff.notEqual(ff.property("double"), ff.literal(0.1));
        PreFilter predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.notEqual(ff.property("int"), ff.literal(1));
        predicate = preFilter(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = ff.notEqual(ff.property("nonMaterializedProperty"), ff.literal(0.5));
        predicate = preFilter(filter);

        assertTrue(isAcceptEverything(predicate));

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsGreaterThanFilter() throws Exception {
        PropertyIsGreaterThan filter = (PropertyIsGreaterThan) toFilter("double > 0.4");
        PreFilter predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (PropertyIsGreaterThan) toFilter("int > 0");
        predicate = preFilter(filter);

        assertFalse(predicate.toString().contains("always")); // verify its not just passing
                                                              // everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = (PropertyIsGreaterThan) toFilter("nonMaterializedProperty > 1");
        predicate = preFilter(filter);

        assertTrue(isAcceptEverything(predicate)); // cannot pre-filter

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsGreaterThanOrEqualToFilter() throws Exception {
        PropertyIsGreaterThanOrEqualTo filter = (PropertyIsGreaterThanOrEqualTo) toFilter(
                "double >= 0.4");
        PreFilter predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (PropertyIsGreaterThanOrEqualTo) toFilter("int >= 1");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = (PropertyIsGreaterThanOrEqualTo) toFilter("nonMaterializedProperty >= 1");
        predicate = preFilter(filter);

        assertTrue(isAcceptEverything(predicate));

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsLessThanFilter() throws Exception {
        PropertyIsLessThan filter = (PropertyIsLessThan) toFilter("double < 5.1");
        PreFilter predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (PropertyIsLessThan) toFilter("int < 1000");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        assertFalse(preFilter(toFilter("int < 1")).apply(testNode));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = (PropertyIsLessThan) toFilter("nonMaterializedProperty < 1");
        predicate = preFilter(filter);

        assertTrue(isAcceptEverything(predicate));

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsLessThanOrEqualToFilter() throws Exception {
        PropertyIsLessThanOrEqualTo filter = (PropertyIsLessThanOrEqualTo) toFilter(
                "double <= 5.1");
        PreFilter predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        assertFalse(preFilter(toFilter("int <= 0.99")).apply(testNode));
        assertFalse(preFilter(toFilter("double <= 0.499")).apply(testNode));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = (PropertyIsLessThanOrEqualTo) toFilter("nonMaterializedProperty <= 1");
        predicate = preFilter(filter);

        assertTrue(isAcceptEverything(predicate));

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsLikeFilter() throws Exception {
        PropertyIsLike filter = (PropertyIsLike) toFilter("string like '%gig'");
        PreFilter predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = (PropertyIsLike) toFilter("nonMaterializedProperty like 'something%'");
        predicate = preFilter(filter);

        assertTrue(isAcceptEverything(predicate));

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsNullFilter() throws Exception {
        PropertyIsNull filter = (PropertyIsNull) toFilter("nullprop is null");
        PreFilter predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (PropertyIsNull) toFilter("int is null");
        predicate = preFilter(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = (PropertyIsNull) toFilter("nonMaterializedProperty is null");
        predicate = preFilter(filter);

        assertTrue(isAcceptEverything(predicate));

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsNilFilter() throws Exception {
        PropertyIsNil filter = ff.isNil(ff.property("nullprop"), "notAvail");
        PreFilter predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.isNil(ff.property("string"), "notAvail");
        predicate = preFilter(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = ff.isNil(ff.property("nonMaterializedAttribute"), "notAvail");
        predicate = preFilter(filter);

        assertTrue(isAcceptEverything(predicate));

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void bboxFilter() throws Exception {
        int x1 = 0;
        int x2 = 180;
        int y1 = 0;
        int y2 = 90;
        Envelope bounds = new Envelope(x1, x2, y1, y2);
        assertEquals(bounds, testNode.bounds().get()); // preflight check

        BBOX filter = ff.bbox("the_geom", x1, y1, x2, y2, "EPSG:4326");
        PreFilter predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate));

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.bbox("the_geom", -10, -10, -1, -1, "EPSG:4326");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate));

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        assertFalse(predicate.apply(testBucket));
    }

    @Test
    public void testContainsFilter() throws Exception {
        Contains filter;
        filter = (Contains) toFilter("contains(the_geom, POLYGON((1 1, 1 2, 2 2, 2 1, 1 1)) )");

        PreFilter predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Contains) toFilter("contains(the_geom, POLYGON((-1 -1, 1 2, 2 2, 2 1, -1 -1)) )");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are filtered by bbox, at the difference of other filters where they evaluate
        // always to true in order for the diff-walk to proceed to its children
        assertFalse(predicate.apply(testBucket));
    }

    @Test
    public void testCrossesFilter() throws Exception {
        Crosses filter;
        filter = (Crosses) toFilter("crosses(the_geom, POLYGON((1 1, 1 2, 2 2, 2 1, 1 1)) )");

        PreFilter predicate = preFilter(filter);
        assertTrue("crosses should have been simplified to intersects for pre-filtering",
                ((PreFilter) predicate).filter instanceof Intersects);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Crosses) toFilter(
                "crosses(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are filtered by bbox, at the difference of other filters where they evaluate
        // always to true in order for the diff-walk to proceed to its children
        assertFalse(predicate.apply(testBucket));
    }

    @Test
    public void testGeometryEqualsFilter() throws Exception {
        Equals filter;
        Polygon bounds = JTS.toGeometry(testNode.bounds().get());
        filter = (Equals) toFilter(String.format("equals(the_geom, %s)", bounds));

        PreFilter predicate = preFilter(filter);
        assertTrue(((PreFilter) predicate).filter instanceof Intersects);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Equals) toFilter(
                "equals(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are filtered by bbox, at the difference of other filters where they evaluate
        // always to true in order for the diff-walk to proceed to its children
        assertFalse(predicate.apply(testBucket));
    }

    @Test
    public void testIntersectsFilter() throws Exception {
        Intersects filter;
        filter = (Intersects) toFilter("Intersects(the_geom, POLYGON((1 1, 1 2, 2 2, 2 1, 1 1)) )");

        PreFilter predicate = preFilter(filter);
        assertTrue(((PreFilter) predicate).filter instanceof Intersects);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Intersects) toFilter(
                "Intersects(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are filtered by bbox, at the difference of other filters where they evaluate
        // always to true in order for the diff-walk to proceed to its children
        assertFalse(predicate.apply(testBucket));
    }

    @Test
    public void testOverlapsFilter() throws Exception {
        Overlaps filter;
        filter = (Overlaps) toFilter("Overlaps(the_geom, POLYGON((1 1, 1 2, 2 2, 2 1, 1 1)) )");

        PreFilter predicate = preFilter(filter);
        assertTrue("Overlaps should have been simplified to intersects for pre-filtering",
                ((PreFilter) predicate).filter instanceof Intersects);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Overlaps) toFilter(
                "Overlaps(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are filtered by bbox, at the difference of other filters where they evaluate
        // always to true in order for the diff-walk to proceed to its children
        assertFalse(predicate.apply(testBucket));
    }

    @Test
    public void testTouchesFilter() throws Exception {
        Envelope bounds = testNode.bounds().get();
        bounds.translate(-1 * bounds.getWidth(), 0);
        Polygon touching = JTS.toGeometry(bounds);

        Touches filter;
        filter = (Touches) toFilter(String.format("Touches(the_geom, %s)", touching));

        PreFilter predicate = preFilter(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Touches) toFilter(
                "Touches(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are filtered by bbox, at the difference of other filters where they evaluate
        // always to true in order for the diff-walk to proceed to its children
        assertFalse(predicate.apply(testBucket));
    }

    @Test
    public void testWithinFilter() throws Exception {
        Envelope bounds = testNode.bounds().get();
        bounds.expandBy(1);
        Polygon container = JTS.toGeometry(bounds);

        Within filter;
        filter = (Within) toFilter(String.format("Within(the_geom, %s)", container));

        PreFilter predicate = preFilter(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Within) toFilter(
                "Within(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are filtered by bbox, at the difference of other filters where they evaluate
        // always to true in order for the diff-walk to proceed to its children
        assertFalse(predicate.apply(testBucket));
    }

    @Test
    public void testDisjointFilter() throws Exception {
        Envelope bounds = testNode.bounds().get();
        bounds.expandBy(1);
        Polygon container = JTS.toGeometry(bounds);

        Disjoint filter;
        filter = (Disjoint) toFilter(String.format("Disjoint(the_geom, %s)", container));

        PreFilter predicate = preFilter(filter);
        assertTrue("Disjoint should have been simplified to intersects for pre-filtering",
                ((PreFilter) predicate).filter instanceof Intersects);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Disjoint) toFilter(
                "Disjoint(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are filtered by bbox, at the difference of other filters where they evaluate
        // always to true in order for the diff-walk to proceed to its children
        assertFalse(predicate.apply(testBucket));
    }

    @Test
    public void testDWithinFilter() throws Exception {
        DWithin filter;
        filter = ff.dwithin(ff.property("the_geom"),
                ff.literal(new WKTReader().read("POINT(0 -1)")), 1.5, "m");

        PreFilter predicate = preFilter(filter);
        assertTrue(((PreFilter) predicate).filter instanceof DWithin);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.dwithin(ff.property("the_geom"),
                ff.literal(new WKTReader().read("POINT(-180 -90)")), 0.5, "m");

        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are filtered by bbox, at the difference of other filters where they evaluate
        // always to true in order for the diff-walk to proceed to its children
        assertFalse(predicate.apply(testBucket));
    }

    @Test
    public void testBeyondFilter() throws Exception {
        Beyond filter;
        filter = ff.beyond(ff.property("the_geom"),
                ff.literal(new WKTReader().read("POINT(-180 0)")), 179, "m");

        PreFilter predicate = preFilter(filter);
        assertTrue(((PreFilter) predicate).filter instanceof Beyond);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.beyond(ff.property("the_geom"),
                ff.literal(new WKTReader().read("POINT(-45 0)")), 50, "m");

        predicate = preFilter(filter);

        assertFalse(isAcceptEverything(predicate)); // verify its not just passing everything

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are filtered by bbox, at the difference of other filters where they evaluate
        // always to true in order for the diff-walk to proceed to its children
        assertFalse(predicate.apply(testBucket));
    }

    @Test
    public void testAfterFilter() throws Exception {
        Date previousDate = new Date();
        previousDate.setTime(DATE_VALUE.getTime() - 1000);

        After filter = ff.after(ff.property("date"), ff.literal(previousDate));

        PreFilter predicate = preFilter(filter);
        assertTrue(((PreFilter) predicate).filter instanceof After);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.after(ff.property("date"), ff.literal(DATE_VALUE));

        predicate = preFilter(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are evaluated to true for the traversal to continue to its leaf nodes
        assertTrue(predicate.apply(testBucket));

        filter = ff.after(ff.property("nonMaterializedProp"), ff.literal(DATE_VALUE));
        predicate = preFilter(filter);

        assertTrue(isAcceptEverything(predicate));

        assertTrue(predicate == PreFilter.INCLUDE);
        assertTrue(predicate.apply(null));
    }

    @Test
    public void testAnyInteractsFilter() throws Exception {
        Date previousDate = new Date();
        previousDate.setTime(DATE_VALUE.getTime() - 1000);

        AnyInteracts filter = ff.anyInteracts(ff.property("date"), ff.literal(DATE_VALUE));

        PreFilter predicate = preFilter(filter);
        assertTrue(((PreFilter) predicate).filter instanceof AnyInteracts);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.anyInteracts(ff.property("date"), ff.literal(previousDate));

        predicate = preFilter(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are evaluated to true for the traversal to continue to its leaf nodes
        assertTrue(predicate.apply(testBucket));

        filter = ff.anyInteracts(ff.property("nonMaterializedProp"), ff.literal(DATE_VALUE));
        predicate = preFilter(filter);

        assertTrue(isAcceptEverything(predicate));

        assertTrue(isAcceptEverything(predicate));// it's Predicates.alwaysTrue()
        assertTrue(predicate.apply(null));
    }

    public boolean isAcceptEverything(PreFilter p) {
        return p.filter.equals(Filter.INCLUDE);
    }

}
