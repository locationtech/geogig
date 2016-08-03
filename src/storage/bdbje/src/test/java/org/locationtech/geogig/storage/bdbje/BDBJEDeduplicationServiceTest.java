/* Copyright (c) 2016 Boundless and others.
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

import org.junit.Test;
import org.locationtech.geogig.plumbing.CreateDeduplicator;
import org.locationtech.geogig.repository.DeduplicationService;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

public class BDBJEDeduplicationServiceTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
        // nothing else to do
    }

    @Test
    public void testSPILookup() {
        DeduplicationService service = geogig.command(CreateDeduplicator.class).call();
        assertTrue(service instanceof BDBJEDeduplicationService);
    }

    @Test
    public void testCreateDeduplicator() {
        DeduplicationService service = geogig.command(CreateDeduplicator.class).call();
        BDBJEDeduplicator deduplicator = (BDBJEDeduplicator) service.createDeduplicator();
        assertNotNull(deduplicator);
        File dbdir = deduplicator.dbdir;
        try {
            assertTrue(dbdir.exists());
            assertTrue(dbdir.getName().startsWith("geogig-deduplicator"));
        } finally {
            deduplicator.release();
            assertFalse(dbdir.exists());
        }
    }
}
