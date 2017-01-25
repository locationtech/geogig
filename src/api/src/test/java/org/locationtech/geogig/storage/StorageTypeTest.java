/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.locationtech.geogig.repository.RepositoryConnectionException;

public class StorageTypeTest {

    @Test
    public void testKeys() {
        assertEquals("graph", StorageType.GRAPH.key);
        assertEquals("objects", StorageType.OBJECT.key);
        assertEquals("index", StorageType.INDEX.key);
        assertEquals("refs", StorageType.REF.key);
        assertEquals("staging", StorageType.STAGING.key);
        assertEquals(5, StorageType.values().length);
    }

    @Test
    public void testConfigure() throws RepositoryConnectionException {
        ConfigDatabase testConfig = new TestConfigDatabase();
        StorageType.GRAPH.configure(testConfig, "testGraph", "1.0");
        StorageType.OBJECT.configure(testConfig, "testObject", "2.0");
        StorageType.INDEX.configure(testConfig, "testIndex", "3.0");
        StorageType.REF.configure(testConfig, "testRef", "4.0");
        StorageType.STAGING.configure(testConfig, "testStaging", "5.0");

        assertEquals("testGraph", testConfig.get("storage.graph").get());
        assertEquals("1.0", testConfig.get("testGraph.version").get());
        assertEquals("testObject", testConfig.get("storage.objects").get());
        assertEquals("2.0", testConfig.get("testObject.version").get());
        assertEquals("testIndex", testConfig.get("storage.index").get());
        assertEquals("3.0", testConfig.get("testIndex.version").get());
        assertEquals("testRef", testConfig.get("storage.refs").get());
        assertEquals("4.0", testConfig.get("testRef.version").get());
        assertEquals("testStaging", testConfig.get("storage.staging").get());
        assertEquals("5.0", testConfig.get("testStaging.version").get());

        StorageType.GRAPH.verify(testConfig, "testGraph", "1.0");
        StorageType.OBJECT.verify(testConfig, "testObject", "2.0");
        StorageType.INDEX.verify(testConfig, "testIndex", "3.0");
        StorageType.REF.verify(testConfig, "testRef", "4.0");
        StorageType.STAGING.verify(testConfig, "testStaging", "5.0");
    }

    @Test
    public void testConfigureExisting() throws RepositoryConnectionException {
        ConfigDatabase testConfig = new TestConfigDatabase();
        StorageType.GRAPH.configure(testConfig, "testGraph", "1.0");
        // reconfiguring with same format name and version should work just fine
        StorageType.GRAPH.configure(testConfig, "testGraph", "1.0");

        try {
            // Different version
            StorageType.GRAPH.configure(testConfig, "testGraph", "2.0");
            fail();
        } catch (RepositoryConnectionException e) {
            // expected
        }

        try {
            // Different format name
            StorageType.GRAPH.configure(testConfig, "testGraph2", "1.0");
            fail();
        } catch (RepositoryConnectionException e) {
            // expected
        }
    }

    @Test
    public void testVerify() throws RepositoryConnectionException {
        ConfigDatabase testConfig = new TestConfigDatabase();
        // nothing configured
        boolean verified = StorageType.GRAPH.verify(testConfig, "testGraph", "1.0");
        assertFalse(verified);

        testConfig.put("storage.graph", "testGraph");
        try {
            // only format name configured
            StorageType.GRAPH.verify(testConfig, "testGraph", "1.0");
            fail();
        } catch (RepositoryConnectionException e) {
            // expected
        }

        testConfig.remove("storage.graph");
        testConfig.put("testGraph.version", "1.0");

        // only the format version was configured
        verified = StorageType.GRAPH.verify(testConfig, "testGraph", "1.0");
        assertFalse(verified);

        testConfig.put("storage.graph", "testGraph");
        try {
            // format name mismatch
            StorageType.GRAPH.verify(testConfig, "testGraph2", "1.0");
            fail();
        } catch (RepositoryConnectionException e) {
            // expected
        }

        try {
            // version mismatch
            StorageType.GRAPH.verify(testConfig, "testGraph", "2.0");
            fail();
        } catch (RepositoryConnectionException e) {
            // expected
        }

        verified = StorageType.GRAPH.verify(testConfig, "testGraph", "1.0");
        assertTrue(verified);
    }
}
