/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration.repository;

import java.util.Arrays;

import org.junit.Test;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

public class RefDatabaseTest extends RepositoryTestCase {

    private RefDatabase refDb;

    @Override
    protected void setUpInternal() throws Exception {
        refDb = repo.refDatabase();
    }

    @Test
    public void testEmpty() {
        assertEquals(ObjectId.NULL.toString(), refDb.getRef(Ref.MASTER));
        assertEquals(Ref.MASTER, refDb.getSymRef(Ref.HEAD));
    }

    @Test
    public void testPutGetRef() {
        byte[] raw = new byte[20];
        Arrays.fill(raw, (byte) 1);
        ObjectId oid = new ObjectId(raw);

        assertEquals(ObjectId.NULL.toString(), refDb.getRef(Ref.MASTER));

        refDb.putRef(Ref.MASTER, oid.toString());

        assertEquals(oid.toString(), refDb.getRef(Ref.MASTER));
    }

    @Test
    public void testPutGetSymRef() {

        String branch = "refs/heads/branch";

        assertEquals(Ref.MASTER, refDb.getSymRef(Ref.HEAD));

        refDb.putSymRef(Ref.HEAD, branch);

        assertEquals(branch, refDb.getSymRef(Ref.HEAD));
    }
}
