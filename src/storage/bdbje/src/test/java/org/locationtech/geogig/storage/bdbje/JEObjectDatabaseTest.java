/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.bdbje;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;

public class JEObjectDatabaseTest extends Assert {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private TestPlatform platform;

    private Hints hints;

    private ObjectDatabase db;

    // instance variable so its reused as if it were the singleton in the guice config
    private EnvironmentBuilder envProvider;

    @Before
    public void setUp() {
        File root = folder.getRoot();
        folder.newFolder(".geogig");
        File home = folder.newFolder("home");
        platform = new TestPlatform(root);
        platform.setUserHome(home);
        hints = new Hints();

        envProvider = new EnvironmentBuilder(platform);

    }

    private ObjectDatabase createDb() {
        ConfigDatabase configDB = new IniFileConfigDatabase(platform);
        JEObjectDatabase db = new JEObjectDatabase_v0_1(configDB, envProvider, hints);
        db.open();
        return db;
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void testReadOnlyHint() {
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.TRUE);
        db = createDb();
        RevObject obj = RevTree.EMPTY;
        try {
            db.put(obj);
            fail("Expected UOE on read only hint");
        } catch (UnsupportedOperationException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testReadOnlyHintPreservedOnReopen() {
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.TRUE);
        db = createDb();
        RevObject obj = RevTree.EMPTY;
        try {
            db.put(obj);
            fail("Expected UOE on read only hint");
        } catch (UnsupportedOperationException e) {
            assertTrue(true);
        }

        db.close();
        db.open();
        try {
            db.put(obj);
            fail("Expected UOE on read only hint");
        } catch (UnsupportedOperationException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testReadOnlyHint2() {
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.TRUE);
        db = createDb();
        RevObject obj = RevTree.EMPTY;
        try {
            db.put(obj);
            fail("Expected UOE on read only hint");
        } catch (UnsupportedOperationException e) {
            assertTrue(true);
        }

        db.close();
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.FALSE);
        db = createDb();

        Assert.assertTrue(db.put(obj));
    }

    @Test
    public void testReadOnlyHint3() {
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.TRUE);
        db = createDb();
        RevObject obj = RevTree.EMPTY;
        try {
            db.put(obj);
            fail("Expected UOE on read only hint");
        } catch (UnsupportedOperationException e) {
            assertTrue(true);
        }

        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.FALSE);
        ObjectDatabase db2 = createDb();

        Assert.assertTrue(db2.put(obj));
        db.close();
        db2.close();
    }

    public void testMultipleInstances() {
        ObjectDatabase db1 = createDb();
        ObjectDatabase db2 = createDb();

        RevObject obj = RevTree.EMPTY;

        assertTrue(db1.put(obj));
        db1.close();
        assertFalse(db2.put(obj));
        db2.close();

        RevObject revObject = db.get(obj.getId());
        assertEquals(obj, revObject);
    }
}
