/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.geotools.plumbing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.geotools.cli.porcelain.TestHelper;

import com.google.common.base.Optional;

public class ListOpTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testNullDataStore() throws Exception {
        ListOp list = new ListOp();
        exception.expect(GeoToolsOpException.class);
        list.call();
    }

    @Test
    public void testEmptyDataStore() throws Exception {
        ListOp list = new ListOp();
        list.setDataStore(TestHelper.createEmptyTestFactory().createDataStore(null));
        Optional<List<String>> features = list.call();
        assertFalse(features.isPresent());
    }

    @Test
    public void testTypeNameException() throws Exception {
        ListOp list = new ListOp();
        list.setDataStore(TestHelper.createFactoryWithGetNamesException().createDataStore(null));
        exception.expect(GeoToolsOpException.class);
        list.call();
    }

    @Test
    public void testList() throws Exception {
        ListOp list = new ListOp();
        list.setDataStore(TestHelper.createTestFactory().createDataStore(null));
        Optional<List<String>> features = list.call();
        assertTrue(features.isPresent());

        assertTrue(features.get().contains("table1"));
        assertTrue(features.get().contains("table2"));
    }
}
