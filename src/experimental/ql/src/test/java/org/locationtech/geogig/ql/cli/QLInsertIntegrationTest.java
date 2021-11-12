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

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.ql.porcelain.QLInsert.parse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.store.FeatureIteratorIterator;
import org.junit.After;
import org.junit.Test;
import org.locationtech.geogig.geotools.adapt.GT;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.ql.porcelain.QLInsert;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;

import com.google.common.collect.Maps;

import net.sf.jsqlparser.statement.insert.Insert;

public class QLInsertIntegrationTest extends RepositoryTestCase {

    private QLTestHelper helper;

    @Override
    public void setUpInternal() throws Exception {
        insertAndAdd(points1, points2, points3);
        insertAndAdd(lines1, lines2, lines3);
        insertAndAdd(poly1, poly2, poly3);

        repo.command(CommitOp.class).call();
        insertAndAdd(points1_modified);
        repo.command(CommitOp.class).call();

        helper = new QLTestHelper(repo);
    }

    @After
    public void after() {
        helper.close();
    }

    private void validate(String st) {
        Insert parsed = parse(st);
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
        validate("insert into tree (pp,ip,sp) values('POINT(1 1)', 101, 'a string')");
        validate(
                "insert into tree (\"@id\", pp,ip,sp) values('myProvidedId', 'POINT(1 1)', 101, 'a string')");

        validate("insert into tree values('POINT(1 1)', 101, 'a string')");

        validate("insert into tree (ip, pp) values(101, 's1'), (102, 's2'), (103, 's3')");

        validate(
                "insert into tree (\"@id\", pp,ip,sp) values('newid', 'POINT(1 1)', 101, 'a string')");

        validate("insert into tree select * from tree2");

        validate("insert into tree select pp, ip from tree2 where ip > 1000");
        validate(
                "insert into tree select pp, ip from tree2 where intersects(pp, 'POLYGON((1 1, 2 2, 3 3, 4 4, 1 1))')");

        // casting
        validate("insert into tree select 1, 2::long, 'a string', cast('POINT(2 2)' as Point)");
        validate(
                "insert into tree select pp::MultiPoint as mp, CAST(ip as long) as lp, sp::UUID as uuid from tree2 where ip > 1000");

        // upsert
        validate("insert ignore into tree  values(1,2,3)");
        validate("insert ignore into tree select * from tree2");

        validate("INSERT INTO Points select * from PointsOld ON DUPLICATE KEY UPDATE ip = ip + 1");
    }

    private DiffObjectCount insert(String sql) {
        DiffObjectCount count = repo.command(QLInsert.class).setStatement(sql).call().get();
        return count;
    }

    @Test
    public void insertFullTuple() {
        String sql = "insert into Points(ip, sp, pp) values (7, 'siete', 'POINT(7 7)')";
        assertEquals(1, insert(sql).getFeaturesAdded());
        SimpleFeature inserted = getFeature("select * from \"WORK_HEAD:Points\" where ip = 7");

        assertFeature(inserted, null, Map.of("ip", 7, "sp", "siete", "pp", geom("POINT(7 7)")));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void insertMultipleTuples() {
        String sql = "insert into Points(ip, sp) values (1, 'one'), (2, 'two'), (3, 'three')";
        assertEquals(3, insert(sql).getFeaturesAdded());

        Map<String, SimpleFeature> inserted = getFeatures(
                "select * from Points where ip IN (1,2,3)");

        assertFeatures(inserted, //
                map("ip", 1, "sp", "one"), //
                map("ip", 2, "sp", "two"), //
                map("ip", 3, "sp", "three"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void insertMultipleTuplesExplicitFID() {
        String sql = "insert into Points(\"@Id\", ip, sp) values ('id1', 1, 'one'), ('id2', 2, 'two'), ('id3', 3, 'three')";
        assertEquals(3, insert(sql).getFeaturesAdded());

        Map<String, SimpleFeature> inserted = getFeatures(
                "select * from Points where ip IN (1,2,3)");

        assertFeatures(inserted, //
                map("@id", "id1", "ip", 1, "sp", "one"), //
                map("@id", "id2", "ip", 2, "sp", "two"), //
                map("@id", "id3", "ip", 3, "sp", "three"));
    }

    Map<String, Object> map(String k1, Object v1, String k2, Object v2) {
        return Map.of(k1, v1, k2, v2);
    }

    Map<String, Object> map(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        return Map.of(k1, v1, k2, v2, k3, v3);
    }

    Map<String, Object> map(String k1, Object v1, String k2, Object v2, String k3, Object v3,
            String k4, Object v4) {
        return Map.of(k1, v1, k2, v2, k3, v3, k4, v4);
    }

    @Test
    public void insertFullTupleWithExplicitFID() {
        String sql = "insert into Points(\"@id\", ip, sp, pp) values ('newid.1', 7, 'siete', 'POINT(7 7)')";
        assertEquals(1, insert(sql).getFeaturesAdded());

        SimpleFeature inserted = getFeature("select * from \"WORK_HEAD:Points\" where ip = 7");
        assertFeature(inserted, "newid.1",
                Map.of("ip", 7, "sp", "siete", "pp", geom("POINT(7 7)")));
    }

    @Test
    public void insertSelectAllTargetDoesNotexist() {
        String sql = "insert into Points2 select * from Points";
        Exception e = assertThrows(IllegalArgumentException.class, () -> insert(sql));
        assertThat(e.getMessage(), containsString("Points2 does not resolve to a feature tree"));
    }

    @Test
    public void insertSelectAllOntoEmptyTree() throws Exception {
        SimpleFeatureType newType = DataUtilities.createType("Points2", pointsTypeSpec);
        WorkingTree workingTree = repo.context().workingTree();
        workingTree.createTypeTree("Points2", GT.adapt(newType));

        String sql = "insert into Points2 (ip, sp) select * from Points";
        assertEquals(3, insert(sql).getFeaturesAdded());

        Map<String, SimpleFeature> expected = getFeatures("select * from Points");
        Map<String, SimpleFeature> inserted = getFeatures("select * from Points2");

        assertFeatures(inserted, expected);
    }

    private void assertFeatures(Map<String/* fid */, SimpleFeature> actual,
            Map<String/* fid */, SimpleFeature> expected) {

        assertEquals(expected.keySet(), actual.keySet());
        Map<String, Object>[] expectedMaps = toMaps(expected);
        assertFeatures(actual, expectedMaps);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object>[] toMaps(Map<String, SimpleFeature> expected) {
        List<Map<String, Object>> maps = new ArrayList<>(expected.size());
        for (SimpleFeature f : expected.values()) {
            Map<String, Object> values = new HashMap<>();
            values.put("@id", f.getID());
            for (Property p : f.getProperties()) {
                values.put(p.getName().getLocalPart(), p.getValue());
            }
            maps.add(values);
        }
        return maps.toArray(new Map[maps.size()]);
    }

    private void assertFeatures(Map<String/* fid */, SimpleFeature> actual,
            @SuppressWarnings("unchecked") Map<String/* att */, Object/* value */>... expected) {

        assertEquals("Number of features don't match", expected.length, actual.size());
        final Set<String> insertedFids = actual.keySet();

        for (Map<String, Object> expectedFeature : expected) {
            expectedFeature = new HashMap<>(expectedFeature);
            @Nullable
            String expectedFid = (String) expectedFeature.remove("@id");
            if (expectedFid != null) {
                assertTrue(format("expected fid %s not found in insert list: %s", expectedFid,
                        insertedFids), insertedFids.contains(expectedFid));
            }

            boolean found = false;
            for (SimpleFeature inserted : actual.values()) {
                if (equals(expectedFeature, inserted)) {
                    found = true;
                    break;
                }
            }
            assertTrue("Expected feaature not found: " + expectedFeature, found);
        }
    }

    private boolean equals(Map<String, Object> expectedValues, SimpleFeature feature) {

        Set<String> typeAttributes = feature.getFeatureType().getAttributeDescriptors().stream()
                .map(AttributeDescriptor::getLocalName).collect(Collectors.toSet());

        Set<String> attNames = expectedValues.keySet();

        for (String att : attNames) {
            assertTrue(
                    format("FeatureType deoes not contain attribute %s: %s", att, typeAttributes),
                    typeAttributes.contains(att));

            Object expected = expectedValues.get(att);
            Object actual = feature.getAttribute(att);
            if (!Objects.equals(expected, actual)) {
                return false;
            }
        }
        return true;
    }

    private void assertFeature(SimpleFeature feature, @Nullable String id,
            Map<String, Object> expectedValues) {
        if (id != null) {
            assertEquals("Id's don't match", id, feature.getID());
        }
        assertTrue(equals(expectedValues, feature));
    }

    private SimpleFeature getFeature(final String query) {
        Map<String, SimpleFeature> asmap = getFeatures(query);
        assertFalse("query returned no features: " + query, asmap.isEmpty());
        assertEquals("query returned more than one feature: " + asmap.keySet(), 1, asmap.size());
        return asmap.values().iterator().next();
    }

    private Map<String, SimpleFeature> getFeatures(final String query) {
        Map<String, SimpleFeature> map;
        SimpleFeatureCollection col = helper.select(query);
        try (SimpleFeatureIterator features = col.features()) {
            Iterator<SimpleFeature> it = new FeatureIteratorIterator<>(features);
            map = Maps.uniqueIndex(it, (f) -> f.getID());
        }
        return map;
    }
}
