/* Copyright (c) 2013-2016 Boundless and others.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.List;

import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

public class LsTreeOpTest extends RepositoryTestCase {

    protected @Override void setUpInternal() throws Exception {
        boolean onecComitPerFeature = false;
        populate(onecComitPerFeature, points1, points2, points3, lines1, lines2, lines3);
    }

    @Test
    public void testNonRecursiveRootListing() {
        Iterator<NodeRef> iter = repo.command(LsTreeOp.class).call();
        assertEquals(2, Iterators.size(iter));
    }

    @Test
    public void testNonRecursiveTreeListing() {
        Iterator<NodeRef> iter = repo.command(LsTreeOp.class).setStrategy(Strategy.TREES_ONLY)
                .call();
        assertEquals(2, Iterators.size(iter));
    }

    @Test
    public void testRecursiveRootListing() {
        Iterator<NodeRef> iter = repo.command(LsTreeOp.class)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).call();

        assertEquals(6, Iterators.size(iter));
    }

    @Test
    public void testPathListing() {
        Iterator<NodeRef> iter = repo.command(LsTreeOp.class).setReference("Points").call();
        List<NodeRef> nodes = ImmutableList.copyOf(iter);

        assertEquals(3, nodes.size());
        for (NodeRef ref : nodes) {
            ObjectId metadataId = ref.metadataId();
            assertFalse(metadataId.isNull());
        }
    }

    @Test
    public void testRefAndPathListing() {
        Iterator<NodeRef> iter = repo.command(LsTreeOp.class).setReference("HEAD:Points").call();
        List<NodeRef> nodes = ImmutableList.copyOf(iter);
        assertEquals(3, nodes.size());
        for (NodeRef ref : nodes) {
            ObjectId metadataId = ref.metadataId();
            assertFalse(metadataId.isNull());
        }
    }

    @Test
    public void testHEADNonRecursiveRootListing() {
        Iterator<NodeRef> iter = repo.command(LsTreeOp.class).setReference("HEAD").call();
        assertEquals(2, Iterators.size(iter));
    }

    @Test
    public void testHEADNonRecursiveTreeListing() {
        Iterator<NodeRef> iter = repo.command(LsTreeOp.class).setReference("HEAD")
                .setStrategy(Strategy.TREES_ONLY).call();

        assertEquals(2, Iterators.size(iter));
    }

    @Test
    public void testUnexistentPathListing() {
        try {
            repo.command(LsTreeOp.class).setReference("WORK_HEAD:WRONGPATH").call();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testUnexistentOriginListing() {
        try {
            repo.command(LsTreeOp.class).setReference("WRONGORIGIN").call();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testListingWithJustAFeature() {
        Iterator<NodeRef> iter = repo.command(LsTreeOp.class).setReference("Points/Points.1")
                .setStrategy(Strategy.TREES_ONLY).call();

        assertEquals(2, Iterators.size(iter));
    }

}
