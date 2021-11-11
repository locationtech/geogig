/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.plumbing.DiffWorkTree;
import org.locationtech.geogig.porcelain.CleanOp;
import org.locationtech.geogig.storage.AutoCloseableIterator;

public class CleanOpTest extends RepositoryTestCase {

    protected @Override void setUpInternal() throws Exception {
    }

    @Test
    public void testClean() throws Exception {

        insert(points1, points2, points3);

        repo.command(CleanOp.class).call();
        try (AutoCloseableIterator<DiffEntry> deleted = repo.command(DiffWorkTree.class).call()) {
            List<DiffEntry> list = deleted.toList();
            // Check that all the features have been deleted
            assertEquals(0, list.size());
        }
    }

    @Test
    public void testTreeClean() throws Exception {

        insert(points1, points2, points3, lines1);

        repo.command(CleanOp.class).setPath(pointsName).call();
        try (AutoCloseableIterator<DiffEntry> deleted = repo.command(DiffWorkTree.class).call()) {
            List<DiffEntry> list = deleted.toList();
            // Check that all the point features have been deleted but not the line one
            assertEquals(1, list.size());
        }

    }

    @Test
    public void testUnexistentPathRemoval() throws Exception {

        populate(false, points1, points2, points3);

        try {
            repo.command(CleanOp.class).setPath(linesName).call();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

    }

}
