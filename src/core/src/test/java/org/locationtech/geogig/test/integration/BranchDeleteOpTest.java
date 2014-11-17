/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.test.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.BranchDeleteOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CommitOp;

import com.google.common.base.Optional;

public class BranchDeleteOpTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        injector.configDatabase().put("user.name", "groldan");
        injector.configDatabase().put("user.email", "groldan@opengeo.org");
    }

    @Test
    public void NoBranchNameTest() {
        BranchDeleteOp testOp = new BranchDeleteOp();
        testOp.setName(null);

        exception.expect(IllegalStateException.class);

        testOp.call();
    }

    @Test
    public void BranchNotPresentTest() {
        Optional<? extends Ref> branchref = geogig.command(BranchDeleteOp.class)
                .setName("noBranch").call();
        assertEquals(Optional.absent(), branchref);
    }

    @Test
    public void BranchPresentTest() throws Exception {
        insertAndAdd(points1);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        geogig.command(BranchDeleteOp.class).setName("TestBranch").call();

        Optional<Ref> result = geogig.command(RefParse.class).setName("TestBranch").call();

        assertFalse(result.isPresent());
    }

    @Test
    public void BranchIsHeadTest() throws Exception {
        insertAndAdd(points1);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestMasterBranch").call();

        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();

        insertAndAdd(points2);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).call();

        exception.expect(IllegalStateException.class);
        geogig.command(BranchDeleteOp.class).setName("TestBranch").call();
    }

    @Test
    public void InvalidBranchNameTest() throws Exception {
        insertAndAdd(points1);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).call();
        Ref testBranch = geogig.command(BranchCreateOp.class).setName("TestBranch").call();

        testBranch = geogig.command(UpdateRef.class).setName("TestBranch")
                .setNewValue(testBranch.getObjectId()).call().get();

        exception.expect(IllegalArgumentException.class);
        geogig.command(BranchDeleteOp.class).setName("TestBranch").call();
    }
}
