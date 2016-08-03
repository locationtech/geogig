/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.ql.cli;

import static com.google.common.collect.Sets.newHashSet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.referencing.CRS;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.ql.porcelain.QLSelect;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.operation.TransformException;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

import net.sf.jsqlparser.statement.select.Select;

public class QLSelectIntegrationTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private QLTestHelper helper;

    @Override
    public void setUpInternal() throws Exception {
        insertAndAdd(points1);
        insertAndAdd(points2);
        insertAndAdd(lines1);
        insertAndAdd(lines2);

        geogig.command(CommitOp.class).call();

        insertAndAdd(points3);
        insertAndAdd(lines3);

        insertAndAdd(points1_modified);

        geogig.command(CommitOp.class).call();

        helper = new QLTestHelper(geogig);
    }

    @Override
    public void tearDownInternal() throws Exception {
        if (helper != null) {
            helper.close();
        }
    }

    private void validate(String st) {
        Select parsed = QLSelect.parse(st);
        assertNotNull(parsed);
        // System.err.println(parsed);
        String expected = st.toLowerCase().replaceAll(" ", "");
        String actual = parsed.toString().toLowerCase().replaceAll(" ", "");
        assertEquals(expected, actual);
    }

    /**
     * Check ability to parse supported syntax
     */
    @Test
    public void validateSuportedSyntax() {
        validate("select * from points");
        validate("select a,b,c from points");
        validate("select a,b,c + 1 as incremented from points");

        validate("select * from \"HEAD~:points\"");
        validate("select * from \"abc123ff:points\"");
        validate("select * from \"refs/heads/master:points\"");
        validate("select * from \"refs/remotes/origin/dev~2:points\"");
        validate("select * from \"HEAD~:points\"");

        validate("select * from \"spaced points\"");

        validate("select * into points2 from points");
        validate("select a,b,c into points2 from points");

        validate("select * into points2 from points where 1 = 1");
        validate("select * into points2 from points where att = 'value' ");
        validate("select * into points2 from points where @id = 'some-feature-id' ");
        validate("select * into points2 from points where \"@id\" = 'some-feature-id' ");

        validate("select * into points2 from points where att like 'abc%' ");

        validate("select * into points2 from points where bbox(g, -180, -90, 0, 0)  ");

        validate(
                "select * from points where a = 1 AND (b > 0 OR c <= 1) OR (f between 0 and 100 AND bbox(g, -180, -90, 0, 0) ) ");

        validate("select * from points where bbox(g, -180, -90, 0, 0)  limit 10");

        validate("select * from points where bbox(g, -180, -90, 0, 0)  limit 100 offset 10");

    }

    @Test
    public void simpleSelect() {
        helper.selectAndAssert("select * from Points", newHashSet(idP1, idP2, idP3), "sp", "ip",
                "pp");
    }

    @Test
    public void simpleSelectRestrictAttributes() {
        HashSet<String> allFids = newHashSet(idP1, idP2, idP3);
        helper.selectAndAssert("select ip from Points", allFids, "ip");
        helper.selectAndAssert("select pp from Points", allFids, "pp");
        helper.selectAndAssert("select pp, sp from Points", allFids, "sp", "pp");
        helper.selectAndAssert("select sp, ip from Points", allFids, "sp", "ip");
    }

    @Test
    public void simpleRequestedAttributeOrder() {
        checkAttributeOrder(helper.select("select ip,sp,pp from Points"), "ip", "sp", "pp");
        checkAttributeOrder(helper.select("select pp,ip from Points"), "pp", "ip");
        checkAttributeOrder(helper.select("select sp,ip,pp from Points"), "sp", "ip", "pp");
    }

    private void checkAttributeOrder(SimpleFeatureCollection features,
            String... expectedAttributesInOrder) {

        SimpleFeatureType schema = features.getSchema();
        List<String> actual = Lists.transform(schema.getAttributeDescriptors(),
                (a) -> a.getLocalName());
        List<String> expected = Arrays.asList(expectedAttributesInOrder);

        assertEquals(expected, actual);
    }

    @Test
    public void simpleFidFilter() {
        helper.selectAndAssert("select * from \"HEAD:Points\" where @ID = 'Points.1'", idP1);
    }

    @Test
    public void greaterThan() {
        helper.selectAndAssert("select * from \"HEAD~:Points\" where ip > 1000", newHashSet(idP2));
        helper.selectAndAssert("select * from \"Points\" where ip > 1001", newHashSet(idP2, idP3));
    }

    @Test
    public void limit() {
        helper.selectAndAssertCount("select * from \"Points\" limit 1", 1);
        helper.selectAndAssertCount("select * from \"HEAD~:Points\" limit 2", 2);
        helper.selectAndAssertCount("select * from \"HEAD~:Points\" limit 5", 2);// there are only 2
                                                                                 // points in
        // HEAD~
        helper.selectAndAssertCount("select * from \"HEAD:Points\" where ip > 1 limit 1", 1);
    }

    /**
     * Limit syntax is {@code select <columns> from 
     * 
    <table>
     *  [where <predicate>] limit [offset,]<limit>}
     */
    @Test
    public void offsetAndLimit() {
        // offset 3 where there are only 3 features
        helper.selectAndAssertCount("select * from \"Points\" limit 3,1", 0);

        helper.selectAndAssertCount("select * from \"HEAD~:Points\" limit 1", 1);

        helper.selectAndAssertCount("select * from Lines limit 2,2", 1);

        helper.selectAndAssertCount("select * from \"HEAD~:Lines\" where ip < 3000 limit 1,5", 1);
        helper.selectAndAssertCount("select * from \"HEAD~:Lines\" where ip < 3000 limit 0,5", 2);
    }

    @Test
    public void between() {
        helper.selectAndAssert("select * from Points where ip between 2000 and 3000",
                newHashSet(idP2, idP3));
    }

    @Test
    public void like() {
        helper.selectAndAssert("select ip,pp from Points where sp like '%a'", idP1);
        helper.selectAndAssertCount("select ip,pp from \"HEAD~:Points\" where sp like '%a'", 0);
    }

    @Test
    public void bboxFilter() {
        helper.selectAndAssert("select * from Points where BBOX(pp, 1.5, 1.5, 2.5, 2.5)", idP2);
        helper.selectAndAssert("select * from Points where BBOX(pp, 1.5, 1.5, 3.5, 3.5)", idP2,
                idP3);
        helper.selectAndAssertCount("select * from Points where BBOX(pp, 3.5, 3.5, 4, 4)", 0);
    }

    @Test
    public void bboxCRSReprojection()
            throws NoSuchAuthorityCodeException, TransformException, FactoryException {
        BoundingBox wgs84Bounds = lines2.getBounds();
        GeneralEnvelope wmBounds = CRS.transform(wgs84Bounds, CRS.decode("EPSG:3857"));

        String bboxStr = wmBounds.getMinimum(0) + ", " + wmBounds.getMinimum(1) + ", "
                + wmBounds.getMaximum(0) + ", " + wmBounds.getMaximum(1);

        String query = "select * from Lines where BBOX(pp, " + bboxStr + ", 'EPSG:3857')";

        helper.selectAndAssert(query, idL2);
    }

    @Test
    public void selectCountAll() {
        SimpleFeatureCollection result = helper.select("select count(*) from Points");
        assertNotNull(result);
        assertSame(QLSelect.COUNT_TYPE, result.getSchema());

        try (SimpleFeatureIterator features = result.features()) {
            assertTrue(features.hasNext());
            SimpleFeature f = features.next();
            assertFalse(features.hasNext());
            assertEquals(3, f.getAttribute("count"));
        }
    }

    @Test
    public void selectCountFilter() {
        SimpleFeatureCollection result = helper
                .select("select count(*) from Points where ip > 2000");
        assertNotNull(result);
        assertSame(QLSelect.COUNT_TYPE, result.getSchema());

        try (SimpleFeatureIterator features = result.features()) {
            assertTrue(features.hasNext());
            SimpleFeature f = features.next();
            assertFalse(features.hasNext());
            assertEquals(1, f.getAttribute("count"));
        }
    }

    @Test
    public void selectBoundsAll() {
        String query = "select bounds(*) from Points";
        testBounds(query, 1, 2, 3, 3, "EPSG:4326");
    }

    @Test
    public void selectBoundsTreeIsh() {
        String query = "select bounds(*) from \"HEAD~:Points\"";
        testBounds(query, 1, 1, 2, 2, "EPSG:4326");
    }

    @Test
    public void selectBoundsQuery() {
        String query = "select bounds(*) from Lines where ip = 3000";
        testBounds(query, 5, 5, 6, 6, "EPSG:4326");
    }

    private void testBounds(String boundsQuery, double minx, double miny, double maxx, double maxy,
            String srs) {
        SimpleFeatureCollection result = helper.selectAndAssert(boundsQuery,
                ImmutableSet.of("bounds"), "minx", "miny", "maxx", "maxy", "crs");

        try (SimpleFeatureIterator features = result.features()) {
            assertTrue(features.hasNext());
            SimpleFeature f = features.next();
            assertFalse(features.hasNext());

            assertEquals(minx, f.getAttribute("minx"));
            assertEquals(miny, f.getAttribute("miny"));
            assertEquals(maxx, f.getAttribute("maxx"));
            assertEquals(maxy, f.getAttribute("maxy"));
            assertEquals(srs, f.getAttribute("crs"));
        }
    }

    @Ignore
    @Test
    public void selectBoundsReprojected() {
        SimpleFeatureCollection result = helper
                .select("select bounds(pp, 'EPSG:4326') from Points");
    }

    @Ignore
    @Test
    public void intersects() {
        // cli.execute("ql",
        // "select * from Points where intersects(pp, geomFromText('POLYGON ((1.5 1.5, 1.5 2.5, 2.5
        // 2.5, 2.5 1.5, 1.5 1.5))'))");
    }

}
