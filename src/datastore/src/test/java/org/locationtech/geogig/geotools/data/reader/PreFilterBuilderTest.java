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
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.NodeRef;
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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTReader;

public class PreFilterBuilderTest {

    private final Date DATE_VALUE = new Date(1486344231314L);

    private final FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

    private Node testNode;

    private NodeRef testNodeRef;

    private Bucket testBucket;

    private PreFilterBuilder builder;

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

        Map<String, Object> extraData = ImmutableMap.of(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA,
                materializedAttributes);

        testNode = Node.create("testFid", oid, metadataId, TYPE.FEATURE, bounds, extraData);

        testNodeRef = new NodeRef(testNode, "fakeLayerName", metadataId);

        ObjectId bucketId = hashString("bucketId");
        testBucket = Bucket.create(bucketId, bounds);

        builder = new PreFilterBuilder(materializedAttributes.keySet());
    }

    @Test
    public void testAttributeIsMaterialized() {
        assertTrue(builder.isMaterialized(ff.property("int")));
        assertTrue(builder.isMaterialized(ff.property("double")));
        assertTrue(builder.isMaterialized(ff.property("date")));
        assertTrue(builder.isMaterialized(ff.property("string")));
        assertFalse(builder.isMaterialized(ff.property("somethingElse")));
    }

    @Test
    public void excludeFilter() {
        Predicate<Bounded> predicate = builder.build(Filter.EXCLUDE);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        assertFalse(predicate.apply(testBucket));
        assertFalse(predicate.apply(null));
    }

    @Test
    public void includeFilter() {
        Predicate<Bounded> predicate = builder.build(Filter.INCLUDE);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));
        assertTrue(predicate.apply(null));
    }

    @Test
    public void andFilter() throws Exception {
        Filter filter = toFilter("int = 1 AND string = 'geogig'");
        Predicate<Bounded> predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));
        assertFalse(predicate.apply(null));

        filter = toFilter("int = 2 AND string = 'geogig'");
        predicate = builder.build(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // when given a bucket, it just must evaluate to true for PreorderDiffWalk to continue
        // traversal
        assertTrue(predicate.apply(testBucket));

        filter = toFilter("int = 2 AND string = 'geogig' AND nonExistent = 'something'");
        predicate = builder.build(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
    }

    @Test
    public void orFilter() throws Exception {
        Filter filter = toFilter("int = 0 OR string = 'geogig'");
        Predicate<Bounded> predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = toFilter("int = 2 OR string = 'something else'");
        predicate = builder.build(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // when given a bucket, it just must evaluate to true for PreorderDiffWalk to continue
        // traversal
        assertTrue(predicate.apply(testBucket));
    }

    @Test
    public void idFilter() throws Exception {
        Filter filter = toFilter("IN ('fake1', 'testFid', 'fake2')");
        Predicate<Bounded> predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = toFilter("IN ('fake1', 'fake2', 'fake3')");
        predicate = builder.build(filter);
        // Id filters short-circuit as TRUE because they're evaluated more efficiently by
        // DiffTree.setPathFilter()
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        // when given a bucket, it just must evaluate to true for PreorderDiffWalk to continue
        // traversal
        assertTrue(predicate.apply(testBucket));
    }

    @Test
    public void notFilter() throws Exception {
        Filter filter = toFilter("NOT(int = 1)");
        Predicate<Bounded> predicate = builder.build(filter);

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = toFilter("NOT(double < 0)");
        predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        // when given a bucket, it just must evaluate to true for PreorderDiffWalk to continue
        // traversal
        assertTrue(predicate.apply(testBucket));
    }

    @Test
    public void propertyIsBetweenFilter() throws Exception {
        Filter filter = toFilter("double between 0.1 and 0.6");
        Predicate<Bounded> predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = toFilter("double between 0.1 and 0.49999");
        predicate = builder.build(filter);

        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // when given a bucket, it just must evaluate to true for PreorderDiffWalk to continue
        // traversal
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = toFilter("nonMaterializedProperty between 0.1 and 0.5");
        predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsEqualToFilter() throws Exception {
        Predicate<Bounded> predicate = builder.build(toFilter("double = 0.5"));
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        predicate = builder.build(toFilter("int = 1.0"));
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        Filter filter = toFilter("nonMaterializedProperty = 1");
        predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsNotEqualToFilter() throws Exception {
        // ECQL.toFilter("double <> 0.1") returns a NOT filter instead of a PropertyIsNotEqualTo
        PropertyIsNotEqualTo filter = ff.notEqual(ff.property("double"), ff.literal(0.1));
        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.notEqual(ff.property("int"), ff.literal(1));
        predicate = builder.build(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = ff.notEqual(ff.property("nonMaterializedProperty"), ff.literal(0.5));
        predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsGreaterThanFilter() throws Exception {
        PropertyIsGreaterThan filter = (PropertyIsGreaterThan) toFilter("double > 0.4");
        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (PropertyIsGreaterThan) toFilter("int > 0");
        predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = (PropertyIsGreaterThan) toFilter("nonMaterializedProperty > 1");
        predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsGreaterThanOrEqualToFilter() throws Exception {
        PropertyIsGreaterThanOrEqualTo filter = (PropertyIsGreaterThanOrEqualTo) toFilter(
                "double >= 0.4");
        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (PropertyIsGreaterThanOrEqualTo) toFilter("int >= 1");
        predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = (PropertyIsGreaterThanOrEqualTo) toFilter("nonMaterializedProperty >= 1");
        predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsLessThanFilter() throws Exception {
        PropertyIsLessThan filter = (PropertyIsLessThan) toFilter("double < 5.1");
        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (PropertyIsLessThan) toFilter("int < 1000");
        predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        assertFalse(builder.build(toFilter("int < 1")).apply(testNode));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = (PropertyIsLessThan) toFilter("nonMaterializedProperty < 1");
        predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsLessThanOrEqualToFilter() throws Exception {
        PropertyIsLessThanOrEqualTo filter = (PropertyIsLessThanOrEqualTo) toFilter(
                "double <= 5.1");
        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        assertFalse(builder.build(toFilter("int <= 0.99")).apply(testNode));
        assertFalse(builder.build(toFilter("double <= 0.499")).apply(testNode));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = (PropertyIsLessThanOrEqualTo) toFilter("nonMaterializedProperty <= 1");
        predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsLikeFilter() throws Exception {
        PropertyIsLike filter = (PropertyIsLike) toFilter("string like '%gig'");
        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = (PropertyIsLike) toFilter("nonMaterializedProperty like 'something%'");
        predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsNullFilter() throws Exception {
        PropertyIsNull filter = (PropertyIsNull) toFilter("nullprop is null");
        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (PropertyIsNull) toFilter("int is null");
        predicate = builder.build(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = (PropertyIsNull) toFilter("nonMaterializedProperty is null");
        predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void propertyIsNilFilter() throws Exception {
        PropertyIsNil filter = ff.isNil(ff.property("nullprop"), "notAvail");
        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.isNil(ff.property("string"), "notAvail");
        predicate = builder.build(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        // if the property being tested is not materialized, pre-filter evaluates to true, in order
        // for the post-filtering to proceed
        filter = ff.isNil(ff.property("nonMaterializedAttribute"), "notAvail");
        predicate = builder.build(filter);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
    }

    @Test
    public void bboxFilter() throws Exception {
        BBOX filter = ff.bbox("the_geom", 0, 0, 180, 90, "EPSG:4326");
        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.bbox("the_geom", -10, -10, -1, -1, "EPSG:4326");
        predicate = builder.build(filter);
        // BBOX filters are short-circuited to TRUE because they're evaluated more efficiently by
        // DiffTree.setBoundsFilter
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        // buckets are filtered by bbox, at the difference of other filters where they evaluate
        // always to true in order for the diff-walk to proceed to its children
        assertTrue(predicate.apply(testBucket));
    }

    @Test
    public void testContainsFilter() throws Exception {
        Contains filter;
        filter = (Contains) toFilter("contains(the_geom, POLYGON((1 1, 1 2, 2 2, 2 1, 1 1)) )");

        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Contains) toFilter("contains(the_geom, POLYGON((-1 -1, 1 2, 2 2, 2 1, -1 -1)) )");
        predicate = builder.build(filter);
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

        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue("crosses should have been simplified to intersects for pre-filtering",
                ((PreFilter) predicate).filter instanceof Intersects);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Crosses) toFilter(
                "crosses(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = builder.build(filter);
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

        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(((PreFilter) predicate).filter instanceof Equals);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Equals) toFilter(
                "equals(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = builder.build(filter);
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

        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(((PreFilter) predicate).filter instanceof Intersects);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Intersects) toFilter(
                "Intersects(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = builder.build(filter);
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

        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue("Overlaps should have been simplified to intersects for pre-filtering",
                ((PreFilter) predicate).filter instanceof Intersects);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Overlaps) toFilter(
                "Overlaps(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = builder.build(filter);
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

        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(((PreFilter) predicate).filter instanceof Touches);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Touches) toFilter(
                "Touches(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = builder.build(filter);
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

        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue("Within should have been simplified to intersects for pre-filtering",
                ((PreFilter) predicate).filter instanceof Intersects);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Within) toFilter(
                "Within(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = builder.build(filter);
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

        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue("Disjoint should have been simplified to intersects for pre-filtering",
                ((PreFilter) predicate).filter instanceof Intersects);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = (Disjoint) toFilter(
                "Disjoint(the_geom, POLYGON((-1 -1, -1 -2, -2 -2, -2 -1, -1 -1)) )");
        predicate = builder.build(filter);
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

        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(((PreFilter) predicate).filter instanceof DWithin);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.dwithin(ff.property("the_geom"),
                ff.literal(new WKTReader().read("POINT(-180 -90)")), 0.5, "m");

        predicate = builder.build(filter);
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

        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(((PreFilter) predicate).filter instanceof Beyond);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.beyond(ff.property("the_geom"),
                ff.literal(new WKTReader().read("POINT(-45 0)")), 50, "m");

        predicate = builder.build(filter);
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

        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(((PreFilter) predicate).filter instanceof After);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.after(ff.property("date"), ff.literal(DATE_VALUE));

        predicate = builder.build(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are evaluated to true for the traversal to continue to its leaf nodes
        assertTrue(predicate.apply(testBucket));

        filter = ff.after(ff.property("nonMaterializedProp"), ff.literal(DATE_VALUE));
        predicate = builder.build(filter);
        assertFalse(predicate instanceof PreFilter);// it's Predicates.alwaysTrue()
        assertTrue(predicate.apply(null));
    }

    @Test
    public void testAnyInteractsFilter() throws Exception {
        Date previousDate = new Date();
        previousDate.setTime(DATE_VALUE.getTime() - 1000);

        AnyInteracts filter = ff.anyInteracts(ff.property("date"), ff.literal(DATE_VALUE));

        Predicate<Bounded> predicate = builder.build(filter);
        assertTrue(((PreFilter) predicate).filter instanceof AnyInteracts);

        assertTrue(predicate.apply(testNode));
        assertTrue(predicate.apply(testNodeRef));
        assertTrue(predicate.apply(testBucket));

        filter = ff.anyInteracts(ff.property("date"), ff.literal(previousDate));

        predicate = builder.build(filter);
        assertFalse(predicate.apply(testNode));
        assertFalse(predicate.apply(testNodeRef));
        // buckets are evaluated to true for the traversal to continue to its leaf nodes
        assertTrue(predicate.apply(testBucket));

        filter = ff.anyInteracts(ff.property("nonMaterializedProp"), ff.literal(DATE_VALUE));
        predicate = builder.build(filter);
        assertFalse(predicate instanceof PreFilter);// it's Predicates.alwaysTrue()
        assertTrue(predicate.apply(null));
    }

}
