/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Juan Marin (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.plumbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.geotools.TestHelper;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

public class DescribeOpTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testNullDataStore() throws Exception {
        DescribeOp describe = new DescribeOp();
        describe.setTable("table1");
        exception.expect(GeoToolsOpException.class);
        describe.call();
    }

    @Test
    public void testNullTable() throws Exception {
        DescribeOp describe = new DescribeOp();
        describe.setDataStore(
                TestHelper.createEmptyTestFactory().createDataStore(ImmutableMap.of()));
        exception.expect(GeoToolsOpException.class);
        describe.call();
    }

    @Test
    public void testEmptyTable() throws Exception {
        DescribeOp describe = new DescribeOp();
        describe.setTable("");
        describe.setDataStore(
                TestHelper.createEmptyTestFactory().createDataStore(ImmutableMap.of()));
        exception.expect(GeoToolsOpException.class);
        describe.call();
    }

    @Test
    public void testEmptyDataStore() throws Exception {
        DescribeOp describe = new DescribeOp();
        describe.setDataStore(
                TestHelper.createEmptyTestFactory().createDataStore(ImmutableMap.of()));
        describe.setTable("table1");
        Optional<Map<String, String>> features = describe.call();
        assertFalse(features.isPresent());
    }

    @Test
    public void testTypeNameException() throws Exception {
        DescribeOp describe = new DescribeOp();
        describe.setDataStore(
                TestHelper.createFactoryWithGetNamesException().createDataStore(ImmutableMap.of()));
        describe.setTable("table1");
        exception.expect(GeoToolsOpException.class);
        describe.call();
    }

    @Test
    public void testGetFeatureSourceException() throws Exception {
        DescribeOp describe = new DescribeOp();
        describe.setDataStore(TestHelper.createFactoryWithGetFeatureSourceException()
                .createDataStore(ImmutableMap.of()));
        describe.setTable("table1");
        exception.expect(GeoToolsOpException.class);
        describe.call();
    }

    @Test
    public void testDescribe() throws Exception {
        DescribeOp describe = new DescribeOp();
        describe.setDataStore(TestHelper.createTestFactory().createDataStore(ImmutableMap.of()));
        describe.setTable("table1");
        Optional<Map<String, String>> properties = describe.call();
        assertTrue(properties.isPresent());

        assertEquals("Point", properties.get().get("geom"));
        assertEquals("String", properties.get().get("label"));
    }
}
