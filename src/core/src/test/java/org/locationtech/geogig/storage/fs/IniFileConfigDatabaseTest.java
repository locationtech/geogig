/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.fs;

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
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Optional;

// TODO: Not sure if this belongs in porcelain or integration

public class IniFileConfigDatabaseTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public final void setUp() {
    }

    @After
    public final void tearDown() {
    }

    @Test
    public void testLocal() {
        final File workingDir = tempFolder.newFolder("mockWorkingDir");
        tempFolder.newFolder("mockWorkingDir/.geogig");

        final Platform platform = mock(Platform.class);
        when(platform.pwd()).thenReturn(workingDir);

        final ConfigDatabase ini = new IniFileConfigDatabase(platform);

        // Test integer and string
        ini.put("section.int", 1);
        ini.put("section.string", "2");

        final int one = ini.get("section.int", int.class).or(-1);
        assertEquals(one, 1);

        final String two = ini.get("section.string").or("-1");
        assertEquals(two, "2");

        // Test overwriting a value that already exists
        ini.put("section.string", "3");

        final String three = ini.get("section.string").or("-1");
        assertEquals(three, "3");
    }

    @Test
    public void testGlobal() {
        final File userhome = tempFolder.newFolder("mockUserHomeDir");

        final Platform platform = mock(Platform.class);
        when(platform.getUserHome()).thenReturn(userhome);

        final ConfigDatabase ini = new IniFileConfigDatabase(platform);

        // Test integer and string
        ini.putGlobal("section.int", 1);
        ini.putGlobal("section.string", "2");

        final int one = ini.getGlobal("section.int", int.class).or(-1);
        assertEquals(one, 1);

        final String two = ini.getGlobal("section.string").or("-1");
        assertEquals(two, "2");

        // Test overwriting a value that already exists
        ini.putGlobal("section.string", "3");

        final String three = ini.getGlobal("section.string").or("-1");
        assertEquals(three, "3");
    }

    @Test
    public void testNoDot() {
        final File workingDir = tempFolder.newFolder("mockWorkingDir");
        tempFolder.newFolder("mockWorkingDir/.geogig");

        final Platform platform = mock(Platform.class);
        when(platform.pwd()).thenReturn(workingDir);

        final ConfigDatabase ini = new IniFileConfigDatabase(platform);

        exception.expect(ConfigException.class);
        ini.get("nodot");
    }

    @Test
    public void testNoSection() {
        final File workingDir = tempFolder.newFolder("mockWorkingDir");
        tempFolder.newFolder("mockWorkingDir/.geogig");

        final Platform platform = mock(Platform.class);
        when(platform.pwd()).thenReturn(workingDir);

        final ConfigDatabase ini = new IniFileConfigDatabase(platform);

        exception.expect(ConfigException.class);
        ini.get(".int");
    }

    @Test
    public void testNoKey() {
        final File workingDir = tempFolder.newFolder("mockWorkingDir");
        tempFolder.newFolder("mockWorkingDir/.geogig");

        final Platform platform = mock(Platform.class);
        when(platform.pwd()).thenReturn(workingDir);

        final ConfigDatabase ini = new IniFileConfigDatabase(platform);

        exception.expect(ConfigException.class);
        ini.get("section.");
    }

    @Test
    public void testNoRepository() {
        final File workingDir = tempFolder.newFolder("mockWorkingDir");
        final Platform platform = mock(Platform.class);
        when(platform.pwd()).thenReturn(workingDir);

        final ConfigDatabase ini = new IniFileConfigDatabase(platform);

        exception.expect(ConfigException.class);
        ini.put("section.int", 1);
    }

    @Test
    public void testNoUserHome() {
        final Platform platform = mock(Platform.class);
        when(platform.getUserHome()).thenReturn(null);

        final ConfigDatabase ini = new IniFileConfigDatabase(platform);

        exception.expect(ConfigException.class);
        ini.putGlobal("section.int", 1);
    }

    @Test
    public void testNullSectionKeyPair() {
        final File workingDir = tempFolder.newFolder("mockWorkingDir");
        tempFolder.newFolder("mockWorkingDir/.geogig");

        final Platform platform = mock(Platform.class);
        when(platform.pwd()).thenReturn(workingDir);

        final ConfigDatabase ini = new IniFileConfigDatabase(platform);

        exception.expect(ConfigException.class);
        ini.get(null);
    }

    @Test
    public void testNullValue() {
        final File workingDir = tempFolder.newFolder("mockWorkingDir");
        tempFolder.newFolder("mockWorkingDir/.geogig");

        final Platform platform = mock(Platform.class);
        when(platform.pwd()).thenReturn(workingDir);

        final ConfigDatabase ini = new IniFileConfigDatabase(platform);

        ini.put("section.null", null);

        Optional<String> str = ini.get("section.null");
        assertFalse(str.isPresent());
    }

    @Test
    public void testNumberFormatException() {
        final File workingDir = tempFolder.newFolder("mockWorkingDir");
        tempFolder.newFolder("mockWorkingDir/.geogig");

        final Platform platform = mock(Platform.class);
        when(platform.pwd()).thenReturn(workingDir);

        final ConfigDatabase ini = new IniFileConfigDatabase(platform);

        ini.put("section.string", "notanumber");

        exception.expect(IllegalArgumentException.class);
        ini.get("section.string", int.class);
    }

    @Test
    public void testNoValue() {
        final File workingDir = tempFolder.newFolder("mockWorkingDir");
        tempFolder.newFolder("mockWorkingDir/.geogig");

        final Platform platform = mock(Platform.class);
        when(platform.pwd()).thenReturn(workingDir);

        final ConfigDatabase ini = new IniFileConfigDatabase(platform);

        Optional<String> str = ini.get("doesnt.exist");
        assertFalse(str.isPresent());
    }
}
