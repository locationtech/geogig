/*******************************************************************************
 * Copyright (c) 2012, 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.test.integration;

import java.util.ArrayList;
import java.util.Iterator;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.plumbing.DiffWorkTree;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.porcelain.CleanOp;

import com.google.common.collect.Lists;

public class CleanOpTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Test
    public void testClean() throws Exception {

        insert(points1, points2, points3);

        geogig.command(CleanOp.class).call();
        Iterator<DiffEntry> deleted = geogig.command(DiffWorkTree.class).call();
        ArrayList<DiffEntry> list = Lists.newArrayList(deleted);
        // Check that all the features have been deleted
        assertEquals(0, list.size());
    }

    @Test
    public void testTreeClean() throws Exception {

        insert(points1, points2, points3, lines1);

        geogig.command(CleanOp.class).setPath(pointsName).call();
        Iterator<DiffEntry> deleted = geogig.command(DiffWorkTree.class).call();
        ArrayList<DiffEntry> list = Lists.newArrayList(deleted);
        // Check that all the point features have been deleted but not the line one
        assertEquals(1, list.size());

    }

    @Test
    public void testUnexistentPathRemoval() throws Exception {

        populate(false, points1, points2, points3);

        try {
            geogig.command(CleanOp.class).setPath(linesName).call();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

    }

}
