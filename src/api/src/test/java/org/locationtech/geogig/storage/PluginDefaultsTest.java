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

import org.junit.Test;

public class PluginDefaultsTest {

    @Test
    public void testConstructorsAndAccessors() {
        final VersionedFormat objectsFormat = new VersionedFormat("objects", "1.0",
                ObjectDatabase.class);
        final VersionedFormat refsFormat = new VersionedFormat("refs", "1.0", RefDatabase.class);
        final VersionedFormat indexFormat = new VersionedFormat("index", "1.0",
                IndexDatabase.class);
        final VersionedFormat conflictsFormat = new VersionedFormat("conflicts", "1.0",
                ConflictsDatabase.class);

        PluginDefaults defaults = new PluginDefaults(objectsFormat, refsFormat, indexFormat,
                conflictsFormat);
        assertEquals(objectsFormat, defaults.getObjects().get());
        assertEquals(refsFormat, defaults.getRefs().get());
        assertEquals(indexFormat, defaults.getIndex().get());
        assertEquals(conflictsFormat, defaults.getConflicts().get());

        defaults = new PluginDefaults(null, null, null, null);
        assertFalse(defaults.getObjects().isPresent());
        assertFalse(defaults.getRefs().isPresent());
        assertFalse(defaults.getIndex().isPresent());

        StorageProvider testProvider = new StorageProvider() {
            @Override
            public String getName() {
                return "test";
            }

            @Override
            public String getVersion() {
                return "1";
            }

            @Override
            public String getDescription() {
                return "test storage provider";
            }

            @Override
            public VersionedFormat getObjectDatabaseFormat() {
                return objectsFormat;
            }

            @Override
            public VersionedFormat getRefsDatabaseFormat() {
                return refsFormat;
            }

            @Override
            public VersionedFormat getIndexDatabaseFormat() {
                return indexFormat;
            }

            public @Override VersionedFormat getConflictsDatabaseFormat() {
                return conflictsFormat;
            }

        };

        defaults = new PluginDefaults(testProvider);
        assertEquals(objectsFormat, defaults.getObjects().get());
        assertEquals(refsFormat, defaults.getRefs().get());
        assertEquals(indexFormat, defaults.getIndex().get());
    }

    @Test
    public void testMutators() {
        final VersionedFormat objectsFormat = new VersionedFormat("objects", "1.0",
                ObjectDatabase.class);
        final VersionedFormat refsFormat = new VersionedFormat("refs", "1.0", RefDatabase.class);
        final VersionedFormat indexFormat = new VersionedFormat("index", "1.0",
                IndexDatabase.class);
        final VersionedFormat conflictsFormat = new VersionedFormat("conflicts", "1.0",
                ConflictsDatabase.class);
        PluginDefaults defaults = new PluginDefaults(null, null, null, null);
        assertFalse(defaults.getObjects().isPresent());
        assertFalse(defaults.getRefs().isPresent());
        assertFalse(defaults.getIndex().isPresent());

        defaults.setObjects(objectsFormat);
        defaults.setRefs(refsFormat);
        defaults.setIndex(indexFormat);
        defaults.setConflicts(conflictsFormat);

        assertEquals(objectsFormat, defaults.getObjects().get());
        assertEquals(refsFormat, defaults.getRefs().get());
        assertEquals(indexFormat, defaults.getIndex().get());
        assertEquals(conflictsFormat, defaults.getConflicts().get());
    }
}
