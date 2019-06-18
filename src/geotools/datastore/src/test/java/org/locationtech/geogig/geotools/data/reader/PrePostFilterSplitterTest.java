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
import static org.opengis.filter.Filter.EXCLUDE;
import static org.opengis.filter.Filter.INCLUDE;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.visitor.SimplifyingFilterVisitor;
import org.geotools.geometry.jts.JTS;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.Or;
import org.opengis.filter.PropertyIsNil;
import org.opengis.filter.PropertyIsNotEqualTo;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.PropertyName;
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

import com.google.common.collect.ImmutableMap;

public class PrePostFilterSplitterTest {

    private final Date DATE_VALUE = new Date(1486344231314L);

    private final FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

    private Node testNode;

    private PrePostFilterSplitter builder;

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
        materializedAttributes.put("materialized_geom", null);

        Map<String, Object> extraData = ImmutableMap.of(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA,
                materializedAttributes);

        testNode = RevObjectFactory.defaultInstance().createNode("testFid", oid, metadataId,
                TYPE.FEATURE, bounds, extraData);

        builder = new PrePostFilterSplitter().extraAttributes(materializedAttributes.keySet());
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
        builder.filter(EXCLUDE).build();
        assertEquals(EXCLUDE, builder.getPreFilter());
        assertEquals(EXCLUDE, builder.getPostFilter());
    }

    @Test
    public void nilFilter() throws Exception {
        PropertyIsNil filter;
        filter = ff.isNil(ff.property("string"), "not avail");
        assertFullySupported(filter);
        filter = ff.isNil(ff.property("nonmaterialized"), "not avail");
        assertFullyUnsupported(filter);
    }

    @Test
    public void includeFilter() {
        builder.filter(Filter.INCLUDE).build();
        assertEquals(INCLUDE, builder.getPreFilter());
        assertEquals(INCLUDE, builder.getPostFilter());
    }

    private void assertFullySupported(String cql) throws Exception {
        assertFilter(cql, cql, "INCLUDE");
    }

    private void assertFullySupported(Filter filter) throws Exception {
        assertFilter(filter, filter, INCLUDE);
    }

    private void assertFullyUnsupported(String cql) throws Exception {
        assertFilter(cql, "INCLUDE", cql);
    }

    private void assertFullyUnsupported(Filter filter) throws Exception {
        assertFilter(filter, INCLUDE, filter);
    }

    private void assertFilter(String cql, String expectedPreFilter, String expectedPostFilter)
            throws Exception {
        Filter filter = toFilter(cql);
        Filter preExpected = toFilter(expectedPreFilter);
        Filter postExpected = toFilter(expectedPostFilter);
        assertFilter(filter, preExpected, postExpected);
    }

    private void assertFilter(Filter filter, Filter preExpected, Filter postExpected) {
        builder.filter(filter).build();
        Filter pre = builder.getPreFilter();
        Filter post = builder.getPostFilter();
        assertEquals("pre filter mismatch", preExpected, pre);
        assertEquals("post filter mismatch", postExpected, post);
    }

    @Test
    public void andFilter() throws Exception {
        assertFullySupported("int = 1 AND string = 'geogig'");
        assertFullySupported("1 = int AND 'geogig' = string");
        assertFullyUnsupported("nonMaterialized = 1 AND nonMaterialized2 = 'geogig'");

        String cql = "int = 2 AND nonmat ='xyz'";
        assertFilter(cql, "int = 2", "nonmat ='xyz'");
    }

    @Test
    public void orFilter() throws Exception {
        assertFullySupported("int = 0 OR string = 'geogig'");
        assertFullySupported("0 = int OR 'geogig' = string");
        assertFullyUnsupported("nonMaterialized = 1 OR nonMaterialized2 = 'geogig'");

        String cql = "int = 2 OR nonmat ='xyz'";
        assertFilter(cql, "INCLUDE", cql);
    }

    @Test
    public void idFilter() throws Exception {
        assertFullySupported("IN ('fake1', 'testFid', 'fake2')");
    }

    @Test
    public void notFilter() throws Exception {
        assertFullySupported("NOT(int = 1)");
        assertFullyUnsupported("NOT(nonmat = 1)");
        assertFilter("NOT(int = 1 AND nonmat = 1)", "NOT(int = 1)", "NOT(nonmat = 1)");
        assertFilter("NOT(int = 1 OR nonmat = 1)", "INCLUDE", "NOT(int = 1) AND NOT(nonmat=1)");
    }

    @Test
    public void propertyIsBetweenFilter() throws Exception {
        assertFullySupported("double between 0.1 and 0.6");
        assertFullyUnsupported("nonMaterializedProperty between 0.1 and 0.5");
    }

    @Test
    public void propertyIsEqualToFilter() throws Exception {
        assertFullySupported("double = 0.5");
        assertFullySupported("0.5 = double");
        assertFullySupported("double = double");
        assertFullyUnsupported("double = nonmat");
    }

    @Test
    public void propertyIsNotEqualToFilter() throws Exception {
        // ECQL.toFilter("double <> 0.1") returns a NOT filter instead of a PropertyIsNotEqualTo
        PropertyIsNotEqualTo filter;
        filter = ff.notEqual(ff.property("double"), ff.literal(0.1));
        assertFullySupported(filter);

        filter = ff.notEqual(ff.literal(0.1), ff.property("double"));
        assertFullySupported(filter);

        filter = ff.notEqual(ff.property("int"), ff.property("double"));
        assertFullySupported(filter);

        filter = ff.notEqual(ff.property("nonmaterialized"), ff.property("double"));
        assertFullyUnsupported(filter);
    }

    @Test
    public void propertyIsGreaterThanFilter() throws Exception {
        assertFullySupported("double > 0.4");
        assertFullySupported("0.4 > double");
        assertFullySupported("int > double");
        assertFullyUnsupported("nonmat > 0");
        assertFullyUnsupported("nonmat > double");
    }

    @Test
    public void propertyIsGreaterThanOrEqualToFilter() throws Exception {
        assertFullySupported("double >= 0.4");
        assertFullySupported("0.4 >= double");
        assertFullySupported("int >= double");
        assertFullyUnsupported("nonmap >= double");
    }

    @Test
    public void propertyIsLessThanFilter() throws Exception {
        assertFullySupported("double < 5.1");
        assertFullySupported("5.1 < double");
        assertFullySupported("int < double");
        assertFullyUnsupported("nonmap < double");
    }

    @Test
    public void propertyIsLessThanOrEqualToFilter() throws Exception {
        assertFullySupported("double <= 5.1");
        assertFullySupported("5.1 <= double");
        assertFullySupported("int <= double");
        assertFullyUnsupported("nonmap <= double");
    }

    @Test
    public void testAddExpression() throws Exception {
        assertFullySupported("double = int + 0.5");
        assertFullySupported("1 = double + int");

        assertFullyUnsupported("1 = nonmat + int");
        assertFullyUnsupported("nonmat + int = 1");
    }

    @Test
    public void testDivideExpression() throws Exception {
        assertFullySupported("double = int / 0.5");
        assertFullySupported("1 = double / int");

        assertFullyUnsupported("1 = nonmat / int");
        assertFullyUnsupported("nonmat / int = 1");
    }

    @Test
    public void testMultiplyExpression() throws Exception {
        assertFullySupported("double = int * 0.5");
        assertFullySupported("1 = double * int");

        assertFullyUnsupported("1 = nonmat * int");
        assertFullyUnsupported("nonmat * int = 1");
    }

    @Test
    public void testSubtractExpression() throws Exception {
        assertFullySupported("double = int - 0.5");
        assertFullySupported("1 = double - int");

        assertFullyUnsupported("1 = nonmat - int");
        assertFullyUnsupported("nonmat - int = 1");
    }

    @Test
    public void testFunctionExpression() throws Exception {

        assertFullySupported("ceil(int) = floor(double)");
        assertFullyUnsupported("ceil(nonmat) = floor(double)");

        assertFullySupported("false = in3(int, 0, 1, double)");
        assertFullyUnsupported("false = in3(int, 0, 1, nonmat)");
    }

    @Test
    public void propertyIsLikeFilter() throws Exception {
        assertFullySupported("string like '%gig'");
        assertFullyUnsupported("nonMaterializedProperty like 'something%'");
    }

    @Test
    public void propertyIsNullFilter() throws Exception {
        assertFullySupported("nullprop is null");
        assertFullySupported("int is null");
        assertFullyUnsupported("nonmat is null");
    }

    @Test
    public void propertyIsNilFilter() throws Exception {
        PropertyIsNil filter;
        filter = ff.isNil(ff.property("nullprop"), "notAvail");
        assertFullySupported(filter);

        filter = ff.isNil(ff.property("string"), "notAvail");
        assertFullySupported(filter);

        filter = ff.isNil(ff.property("nonMaterializedAttribute"), "notAvail");
        assertFullyUnsupported(filter);
    }

    @Test
    public void bboxFilter() throws Exception {
        BBOX filter = ff.bbox("nontmaterialized", 0, 0, 180, 90, "EPSG:4326");
        BBOX expected = ff.bbox(ff.property("@bounds"), filter.getBounds());
        assertFilter(filter, expected, INCLUDE);
    }

    @Test
    public void bboxOredFilter() throws Exception {
        BBOX bbox1 = ff.bbox("nonmaterialized", 0, 0, 180, 90, "EPSG:4326");
        BBOX bbox2 = ff.bbox("nonmaterialized", -180, -90, 0, 0, "EPSG:4326");
        Filter filter = ff.or(bbox1, bbox2);

        Or expected = ff.or(//
                ff.bbox(ff.property("@bounds"), bbox1.getBounds()), //
                ff.bbox(ff.property("@bounds"), bbox2.getBounds()));
        assertFilter(filter, expected, INCLUDE);
    }

    private Literal geomLiteral(String wkt) throws Exception {
        return ff.literal(geom(wkt));
    }

    private Geometry geom(String wkt) throws ParseException {
        return new WKTReader().read(wkt);
    }

    @Test
    public void containsFilter() throws Exception {
        Contains filter = ff.contains(ff.property("the_geom"), geomLiteral("POINT(1 1)"));
        Contains pre = ff.contains(ff.property("@bounds"), geomLiteral("POINT(1 1)"));
        Contains post = filter;
        assertFilter(filter, pre, post);
    }

    @Test
    public void crossesFilter() throws Exception {
        Filter filter;
        Filter pre;
        Filter post;

        filter = (Crosses) toFilter("crosses(the_geom, POLYGON((1 1, 1 2, 2 2, 2 1, 1 1)) )");
        pre = ff.intersects(ff.property("@bounds"),
                geomLiteral("POLYGON((1 1, 1 2, 2 2, 2 1, 1 1))"));
        post = filter;
        assertFilter(filter, pre, post);

        filter = (Crosses) toFilter("crosses(the_geom, LINESTRING(1 1, 2 2))");
        pre = ff.intersects(ff.property("@bounds"), geomLiteral("LINESTRING(1 1, 2 2)"));
        post = filter;
        assertFilter(filter, pre, post);
    }

    @Test
    public void testGometryFilterOnMaterializedGeometryProperty() throws Exception {
        Filter filter;
        Filter pre;
        Filter post;

        filter = (Equals) toFilter("equals(materialized_geom, LINESTRING(1 1, 2 2, 3 3) )");
        pre = filter;
        post = INCLUDE;
        assertFilter(filter, pre, post);

        filter = (Intersects) toFilter(
                "Intersects(materialized_geom, POLYGON((1 1, 1 2, 2 2, 2 1, 1 1)) )");
        pre = filter;
        post = INCLUDE;
        assertFilter(filter, pre, post);

    }

    @Test
    public void geometryEqualsFilter() throws Exception {
        Filter filter;
        Filter pre;
        Filter post;
        Geometry geom = geom("LINESTRING(1 1, 2 2, 3 3)");
        Geometry bounds = JTS.toGeometry(geom.getEnvelopeInternal());

        // pre filter checks are a downgrade to an envelope intersects, to account for possibly
        // floating point rounding errors in the bounds saved on the tree Nodes
        filter = (Equals) toFilter("equals(the_geom, " + geom + " )");
        pre = ff.intersects(ff.property("@bounds"), ff.literal(bounds));
        post = filter;
        assertFilter(filter, pre, post);
    }

    @Test
    public void intersectsFilter() throws Exception {
        Filter filter;
        Filter pre;
        Filter post;

        filter = (Intersects) toFilter("Intersects(the_geom, POLYGON((1 1, 1 2, 2 2, 2 1, 1 1)) )");
        pre = ff.intersects(ff.property("@bounds"),
                geomLiteral("POLYGON((1 1, 1 2, 2 2, 2 1, 1 1))"));
        post = filter;
        assertFilter(filter, pre, post);
    }

    @Test
    public void overlapsFilter() throws Exception {
        Filter filter;
        Filter pre;
        Filter post;
        filter = (Overlaps) toFilter("Overlaps(the_geom, POLYGON((1 1, 1 2, 2 2, 2 1, 1 1)) )");
        pre = ff.intersects(ff.property("@bounds"),
                geomLiteral("POLYGON((1 1, 1 2, 2 2, 2 1, 1 1))"));
        post = filter;
        assertFilter(filter, pre, post);
    }

    @Test
    public void touchesFilter() throws Exception {
        Envelope bounds = testNode.bounds().get();
        bounds.translate(-1 * bounds.getWidth(), 0);
        Polygon touching = JTS.toGeometry(bounds);
        // just a preflight test
        assertTrue(JTS.toGeometry(bounds).intersects(touching));

        Touches filter;
        Filter pre;
        Filter post;

        filter = (Touches) toFilter(String.format("Touches(the_geom, %s)", touching));
        pre = ff.intersects(ff.property("@bounds"), ff.literal(touching));
        post = filter;

        assertFilter(filter, pre, post);
    }

    @Test
    public void withinFilter() throws Exception {
        Envelope bounds = testNode.bounds().get();
        bounds.expandBy(1);
        Polygon container = JTS.toGeometry(bounds);

        Within filter;
        Filter pre;
        Filter post;
        filter = (Within) toFilter(String.format("Within(the_geom, %s)", container));
        pre = ff.within(ff.property("@bounds"), ff.literal(container));
        post = filter;

        assertFilter(filter, pre, post);
    }

    @Test
    public void disjointFilter() throws Exception {
        Envelope bounds = testNode.bounds().get();
        bounds.expandBy(1);
        Polygon container = JTS.toGeometry(bounds);
        Geometry containerBounds = JTS.toGeometry(container.getEnvelopeInternal());

        Disjoint filter;
        filter = (Disjoint) toFilter(String.format("Disjoint(the_geom, %s)", container));
        Filter pre = ff.intersects(ff.property("@bounds"), ff.literal(containerBounds));
        Filter post = filter;

        assertFilter(filter, pre, post);
    }

    @Test
    public void dWithinFilter() throws Exception {
        DWithin filter;
        Filter pre;
        Filter post;
        filter = ff.dwithin(ff.property("the_geom"),
                ff.literal(new WKTReader().read("POINT(0 -1)")), 1.5, "m");

        pre = ff.dwithin(ff.property("@bounds"), ff.literal(new WKTReader().read("POINT(0 -1)")),
                1.5, "m");
        post = filter;

        assertFilter(filter, pre, post);
    }

    @Test
    public void beyondFilter() throws Exception {
        Beyond filter;
        Filter pre;
        Filter post;
        filter = ff.beyond(ff.property("the_geom"),
                ff.literal(new WKTReader().read("POINT(-180 0)")), 179, "m");

        pre = ff.beyond(ff.property("@bounds"), ff.literal(new WKTReader().read("POINT(-180 0)")),
                179, "m");
        post = filter;

        assertFilter(filter, pre, post);
    }

    @Test
    public void testTemporalPredicates() throws Exception {
        Date dateVal = new Date();
        dateVal.setTime(DATE_VALUE.getTime() - 1000);

        PropertyName dateProp = ff.property("date");
        PropertyName nonMaterializedProp = ff.property("nonmat");
        Literal dateLiteral = ff.literal(dateVal);

        assertFullySupported(ff.after(dateProp, dateLiteral));
        assertFullySupported(ff.after(dateLiteral, dateProp));
        assertFullyUnsupported(ff.after(dateLiteral, nonMaterializedProp));

        assertFullySupported("date DURING 2016-01-01T00:00:00Z/2017-01-01T00:00:00Z");
        assertFullyUnsupported("nonmat DURING 2016-01-01T00:00:00Z/2017-01-01T00:00:00Z");

        assertFullySupported("date BEFORE 2017-01-01T00:00:00Z");
        assertFullyUnsupported("nonmat BEFORE 2017-01-01T00:00:00Z");

        assertFullySupported(ff.begins(dateProp, dateLiteral));
        assertFullySupported(ff.begins(dateLiteral, dateProp));
        assertFullyUnsupported(ff.begins(nonMaterializedProp, dateLiteral));

        assertFullySupported(ff.begunBy(dateProp, dateLiteral));
        assertFullySupported(ff.begunBy(dateLiteral, dateProp));
        assertFullyUnsupported(ff.begunBy(nonMaterializedProp, dateLiteral));

        assertFullySupported(ff.endedBy(dateProp, dateLiteral));
        assertFullySupported(ff.endedBy(dateLiteral, dateProp));
        assertFullyUnsupported(ff.endedBy(nonMaterializedProp, dateLiteral));

        assertFullySupported(ff.ends(dateProp, dateLiteral));
        assertFullySupported(ff.ends(dateLiteral, dateProp));
        assertFullyUnsupported(ff.ends(nonMaterializedProp, dateLiteral));

        assertFullySupported(ff.meets(dateProp, dateLiteral));
        assertFullySupported(ff.meets(dateLiteral, dateProp));
        assertFullyUnsupported(ff.meets(nonMaterializedProp, dateLiteral));

        assertFullySupported(ff.metBy(dateProp, dateLiteral));
        assertFullySupported(ff.metBy(dateLiteral, dateProp));
        assertFullyUnsupported(ff.metBy(nonMaterializedProp, dateLiteral));

        assertFullySupported(ff.overlappedBy(dateProp, dateLiteral));
        assertFullySupported(ff.overlappedBy(dateLiteral, dateProp));
        assertFullyUnsupported(ff.overlappedBy(nonMaterializedProp, dateLiteral));

        assertFullySupported(ff.tcontains(dateProp, dateLiteral));
        assertFullySupported(ff.tcontains(dateLiteral, dateProp));
        assertFullyUnsupported(ff.tcontains(nonMaterializedProp, dateLiteral));

        assertFullySupported(ff.tequals(dateProp, dateLiteral));
        assertFullySupported(ff.tequals(dateLiteral, dateProp));
        assertFullyUnsupported(ff.tequals(nonMaterializedProp, dateLiteral));

        // commented out cause SimplifyingFilterVisitor at PrePostFilterSplitter.build() converts
        // this toverlaps to a tcontains and I don't know how to create a toverlaps filter that
        // won't be simplified that way
        // assertFullySupported(ff.toverlaps(dateProp, dateLiteral));
        // assertFullySupported(ff.toverlaps(dateLiteral, dateProp));
        // assertFullyUnsupported(ff.toverlaps(nonMaterializedProp, dateLiteral));

        assertFullySupported(ff.anyInteracts(dateProp, ff.literal(DATE_VALUE)));
        assertFullyUnsupported(
                ff.anyInteracts(ff.property("nonmaterialized"), ff.literal(DATE_VALUE)));
    }

    @Test
    public void testMoreComplexFilters() throws Exception {
        Filter filter;
        Filter pre;
        Filter post;

        filter = simplify("date > 1630-01-13T00:00:00.000Z and date < 1630-01-13T00:00:00.000Z and "
                + "bbox(materialized_geom, 1, 1, 2, 2)");
        // BBOX is optimized by another code path and decomposed as INCLUDE
        filter = simplify("date > 1630-01-13T00:00:00.000Z and date < 1630-01-13T00:00:00.000Z and "
                + "bbox(\"@bounds\", 1, 1, 2, 2)");
        post = INCLUDE;
        assertFilter(filter, filter, post);

        filter = simplify(
                "(date > 1630-01-13T00:00:00.000Z and date < 1630-01-13T00:00:00.000Z) and "
                        + "overlaps(materialized_geom, LINESTRING(1 1, 2 2))");
        pre = filter;
        post = INCLUDE;
        assertFilter(filter, pre, post);

        filter = simplify(
                "(nonMatdate > 1630-01-13T00:00:00.000Z and date < 1630-01-13T00:00:00.000Z) and "
                        + "overlaps(materialized_geom, LINESTRING(1 1, 2 2))");

        pre = toFilter("date < 1630-01-13T00:00:00.000Z and "
                + "overlaps(materialized_geom, LINESTRING(1 1, 2 2))");

        post = toFilter("nonMatdate > 1630-01-13T00:00:00.000Z");

        assertFilter(filter, pre, post);
    }

    /**
     * ECQL.toFilter() parses composed filters in pairs (e.g. ((a and b) and c), instead of (a and b
     * and c), which is how the builder will return them. This function uses the same technique to
     * return a simpler filter so we can more easily compare expected/actual
     */
    private Filter simplify(String cql) throws Exception {
        return simplify(toFilter(cql));
    }

    private Filter simplify(Filter filter) throws Exception {
        return SimplifyingFilterVisitor.simplify(filter);
    }
}
