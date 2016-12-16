/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.storage.impl.ConflictsDatabaseConformanceTest;

import com.google.common.collect.ImmutableList;

public class RocksdbConflictsDatabaseConformanceTest
        extends ConflictsDatabaseConformanceTest<RocksdbConflictsDatabase> {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Override
    protected RocksdbConflictsDatabase createConflictsDatabase() throws Exception {
        return new RocksdbConflictsDatabase(tmp.getRoot());
    }

    @Override
    protected void dispose(RocksdbConflictsDatabase conflicts) throws Exception {
        conflicts.close();
    }

    @Test
    public void testDatabaseConflictsPersist() throws Exception {
        final String ns = null;
        Iterable<Conflict> list = ImmutableList.of(c1, c2, c3);
        conflicts.addConflicts(ns, list);
        assertEquals(c1, conflicts.getConflict(ns, c1.getPath()).get());
        assertEquals(c2, conflicts.getConflict(ns, c2.getPath()).get());
        assertEquals(c3, conflicts.getConflict(ns, c3.getPath()).get());

        conflicts.close();

        RocksdbConflictsDatabase database = createConflictsDatabase();
        assertEquals(c1, database.getConflict(ns, c1.getPath()).get());
        assertEquals(c2, database.getConflict(ns, c2.getPath()).get());
        assertEquals(c3, database.getConflict(ns, c3.getPath()).get());
    }

    @Test
    public void testRemoveByNullPrefix() throws Exception {
        final String ns = null;
        Iterable<Conflict> list = ImmutableList.of(c1, c2, c3);
        conflicts.addConflicts(ns, list);
        assertEquals(c1, conflicts.getConflict(ns, c1.getPath()).get());
        assertEquals(c2, conflicts.getConflict(ns, c2.getPath()).get());
        assertEquals(c3, conflicts.getConflict(ns, c3.getPath()).get());
        conflicts.removeByPrefix(ns, null);
        assertFalse(conflicts.getConflict(ns, c1.getPath()).isPresent());
        assertFalse(conflicts.getConflict(ns, c2.getPath()).isPresent());
        assertFalse(conflicts.getConflict(ns, c3.getPath()).isPresent());
    }

}
