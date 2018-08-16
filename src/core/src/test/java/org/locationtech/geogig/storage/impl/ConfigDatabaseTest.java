/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConfigException;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public abstract class ConfigDatabaseTest<C extends ConfigDatabase> {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    protected C config;

    @Before
    public final void setUp() throws IOException {
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
        assertFalse(config.get("section.int").isPresent());
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
    public void testNestedSections() {
        // Test integer and string
        config.put("section.subsection.int", 1);
        config.put("section.subsection.string", "2");

        final int one = config.get("section.subsection.int", Integer.class).or(-1);
        assertEquals(one, 1);

        final String two = config.get("section.subsection.string").or("-1");
        assertEquals(two, "2");

        // Test overwriting a value that already exists
        config.put("section.subsection.string", "3");

        final String three = config.get("section.subsection.string").or("-1");
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
    public void testGetAllSubsections() {
        // Test integer and string
        config.put("section1.subsection1.int", 1);
        config.put("section1.subsection2.string", "2");
        config.put("section1.subsection1.subsub1.int", 1);
        config.put("section1.subsection2.subsub2.string", "2");

        config.put("section2.subsection3.int", 3);
        config.put("section2.subsection4.string", "4");

        List<String> allSubsections = config.getAllSubsections("section1");
        Set<String> expected = ImmutableSet.of("subsection1", "subsection2", "subsection1.subsub1",
                "subsection2.subsub2");
        assertEquals(expected, new HashSet<String>(allSubsections));
    }

    @Test
    public void testGetAllSubsectionsGlobal() {
        // Test integer and string
        config.putGlobal("section1.subsection1.int", 1);
        config.putGlobal("section1.subsection2.string", "2");
        config.putGlobal("section1.subsection1.subsub1.int", 1);
        config.putGlobal("section1.subsection2.subsub2.string", "2");

        config.putGlobal("section2.subsection3.int", 3);
        config.putGlobal("section2.subsection4.string", "4");

        List<String> allSubsections = config.getAllSubsectionsGlobal("section1");
        Set<String> expected = ImmutableSet.of("subsection1", "subsection2", "subsection1.subsub1",
                "subsection2.subsub2");
        assertEquals(expected, new HashSet<String>(allSubsections));
    }

    @Test
    public void testGetAll() {
        // Test integer and string
        config.put("section1.int", 1);
        config.put("section1.subsection.string", "2");
        config.put("section1.subsection.subsub.int", 1);
        config.put("section2.int", 3);
        config.put("section2.subsection.string", "4");

        Map<String, String> all = config.getAll();
        Map<String, String> expected = ImmutableMap.of("section1.int", "1",
                "section1.subsection.string", "2", "section1.subsection.subsub.int", "1",
                "section2.int", "3", "section2.subsection.string", "4");
        // check key by key instead of equals on the maps in case the backend adds some extra config
        // as part of its initialization process
        expected.entrySet().forEach((e) -> assertEquals(e.getValue(), all.get(e.getKey())));
    }

    @Test
    public void testGetAllSectionGlobal() {
        // Test integer and string
        config.putGlobal("section1.int", 1);
        config.putGlobal("section1.subsection.string", "2");
        config.putGlobal("section1.subsection.subsub.int", 1);
        config.putGlobal("section1.subsection.subsub.string", "4");
        config.putGlobal("section2.int", 3);

        assertEquals(ImmutableMap.of("int", "1"), config.getAllSectionGlobal("section1"));
        assertEquals(ImmutableMap.of("string", "2"),
                config.getAllSectionGlobal("section1.subsection"));
        assertEquals(ImmutableMap.of("int", "1", "string", "4"),
                config.getAllSectionGlobal("section1.subsection.subsub"));
    }

    @Test
    public void testGetAllGlobal() {
        // Test integer and string
        config.putGlobal("section1.int", 1);
        config.putGlobal("section1.subsection.string", "2");
        config.putGlobal("section1.subsection.subsub.int", 1);
        config.putGlobal("section2.int", 3);
        config.putGlobal("section2.subsection.string", "4");

        Map<String, String> all = config.getAllGlobal();
        Map<String, String> expected = ImmutableMap.of("section1.int", "1",
                "section1.subsection.string", "2", "section1.subsection.subsub.int", "1",
                "section2.int", "3", "section2.subsection.string", "4");
        assertEquals(expected, all);
    }

    @Test
    public void testGetAllSection() {
        // Test integer and string
        config.put("section1.int", 1);
        config.put("section1.subsection.string", "2");
        config.put("section1.subsection.subsub.int", 1);
        config.put("section1.subsection.subsub.string", "4");
        config.put("section2.int", 3);

        assertEquals(ImmutableMap.of("int", "1"), config.getAllSection("section1"));
        assertEquals(ImmutableMap.of("string", "2"), config.getAllSection("section1.subsection"));
        assertEquals(ImmutableMap.of("int", "1", "string", "4"),
                config.getAllSection("section1.subsection.subsub"));
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

    @Test
    public void testRemove() {
        // Test integer and string
        config.put("section1.int", 1);
        config.put("section1.subsection.string", "2");
        config.put("section1.subsection.subsub.int", 1);
        config.put("section1.subsection.subsub.string", "4");
        config.put("section2.int", 3);

        assertTrue(config.get("section1.int").isPresent());
        config.remove("section1.int");
        assertFalse(config.get("section1.int").isPresent());

        assertTrue(config.get("section1.subsection.subsub.string").isPresent());
        config.remove("section1.subsection.subsub.string");
        assertFalse(config.get("section1.subsection.subsub.string").isPresent());
    }

    @Test
    public void testPutSection() {
        // Test integer and string
        ImmutableMap<String, String> map = ImmutableMap.of(//
                "k1", "v1", //
                "subsection.string", "2", //
                "subsection.int", "1", //
                "subsection.long", "4"//
        );
        config.putSection("section1", map);
        assertEquals(ImmutableMap.of("k1", "v1"), config.getAllSection("section1"));
        assertEquals(ImmutableMap.of("string", "2", "int", "1", "long", "4"),
                config.getAllSection("section1.subsection"));
    }

    @Test
    public void testRemoveSection() {
        // Test integer and string
        config.put("section1.int", 1);
        config.put("section1.subsection.string", "2");
        config.put("section1.subsection.subsub.int", 1);
        config.put("section1.subsection.subsub.string", "4");
        config.put("section2.int", 3);

        assertTrue(config.get("section1.subsection.subsub.string").isPresent());
        assertTrue(config.get("section1.subsection.subsub.int").isPresent());
        config.removeSection("section1.subsection.subsub");
        assertFalse(config.get("section1.subsection.subsub.string").isPresent());
        assertFalse(config.get("section1.subsection.subsub.int").isPresent());

        assertTrue(config.get("section1.int").isPresent());
        config.removeSection("section1");
        assertFalse(config.get("section1.int").isPresent());
    }

    @Test
    public void testRemoveGlobal() {
        // Test integer and string
        config.putGlobal("section1.int", 1);
        config.putGlobal("section1.subsection.string", "2");
        config.putGlobal("section1.subsection.subsub.int", 1);
        config.putGlobal("section1.subsection.subsub.string", "4");
        config.putGlobal("section2.int", 3);

        assertTrue(config.getGlobal("section1.int").isPresent());
        config.removeGlobal("section1.int");
        assertFalse(config.getGlobal("section1.int").isPresent());

        assertTrue(config.getGlobal("section1.subsection.subsub.string").isPresent());
        config.removeGlobal("section1.subsection.subsub.string");
        assertFalse(config.getGlobal("section1.subsection.subsub.string").isPresent());
    }

    @Test
    public void testRemoveSectionGlobal() {
        // Test integer and string
        config.putGlobal("section1.int", 1);
        config.putGlobal("section1.subsection.string", "2");
        config.putGlobal("section1.subsection.subsub.int", 1);
        config.putGlobal("section1.subsection.subsub.string", "4");
        config.putGlobal("section2.int", 3);

        assertTrue(config.getGlobal("section1.subsection.subsub.string").isPresent());
        assertTrue(config.getGlobal("section1.subsection.subsub.int").isPresent());
        config.removeSectionGlobal("section1.subsection.subsub");
        assertFalse(config.getGlobal("section1.subsection.subsub.string").isPresent());
        assertFalse(config.getGlobal("section1.subsection.subsub.int").isPresent());

        assertTrue(config.getGlobal("section1.int").isPresent());
        config.removeSectionGlobal("section1");
        assertFalse(config.getGlobal("section1.int").isPresent());
    }
}