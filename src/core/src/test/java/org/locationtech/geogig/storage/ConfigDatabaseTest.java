/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.porcelain.ConfigException;

import com.google.common.base.Optional;

public abstract class ConfigDatabaseTest<C extends ConfigDatabase> {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    C config;

    @Before
    public final void setUp() {
        final File userhome = tempFolder.newFolder("mockUserHomeDir");

        final File workingDir = tempFolder.newFolder("mockWorkingDir");
        tempFolder.newFolder("mockWorkingDir", ".geogig");

        final Platform platform = mock(Platform.class);
        when(platform.getUserHome()).thenReturn(userhome);
        when(platform.pwd()).thenReturn(workingDir);

        config = createDatabase(platform);
    }

    @After
    public final void tearDown() {
        destroy(config);
    }

    protected abstract C createDatabase(final Platform platform);

    protected abstract void destroy(C config);

    @Test
    public void testLocal() {
        // Test integer and string
        config.put("section.int", 1);
        config.put("section.string", "2");

        final int one = config.get("section.int", Integer.class).or(-1);
        assertEquals(one, 1);

        final String two = config.get("section.string").or("-1");
        assertEquals(two, "2");

        // Test overwriting a value that already exists
        config.put("section.string", "3");

        final String three = config.get("section.string").or("-1");
        assertEquals(three, "3");
    }

    @Test
    public void testGlobal() {
        // Test integer and string
        config.putGlobal("section.int", 1);
        config.putGlobal("section.string", "2");

        final int one = config.getGlobal("section.int", Integer.class).or(-1);
        assertEquals(one, 1);

        final String two = config.getGlobal("section.string").or("-1");
        assertEquals(two, "2");

        // Test overwriting a value that already exists
        config.putGlobal("section.string", "3");

        final String three = config.getGlobal("section.string").or("-1");
        assertEquals(three, "3");
    }

    @Test
    public void testNoDot() {
        exception.expect(ConfigException.class);
        config.get("nodot");
    }

    @Test
    public void testNoSection() {
        exception.expect(ConfigException.class);
        config.get(".int");
    }

    @Test
    public void testNoKey() {
        exception.expect(ConfigException.class);
        config.get("section.");
    }

    @Test
    public void testNoRepository() {
        tempFolder.delete();
        exception.expect(ConfigException.class);
        config.put("section.int", 1);
    }

    @Test
    public void testNoUserHome() {
        final Platform platform = mock(Platform.class);
        when(platform.getUserHome()).thenReturn(null);

        final ConfigDatabase config = createDatabase(platform);

        exception.expect(ConfigException.class);
        config.putGlobal("section.int", 1);
    }

    @Test
    public void testNullSectionKeyPair() {
        exception.expect(ConfigException.class);
        config.get(null);
    }

    @Test
    public void testNullValue() {
        config.put("section.null", null);

        Optional<String> str = config.get("section.null");
        assertFalse(str.isPresent());
    }

    @Test
    public void testNumberFormatException() {
        config.put("section.string", "notanumber");

        exception.expect(IllegalArgumentException.class);
        config.get("section.string", Integer.class);
    }

    @Test
    public void testNoValue() {
        Optional<String> str = config.get("doesnt.exist");
        assertFalse(str.isPresent());
    }

}