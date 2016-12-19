/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.BranchRenameOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;

import com.google.common.base.Optional;

public class BranchRenameOpTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        injector.configDatabase().put("user.name", "groldan");
        injector.configDatabase().put("user.email", "groldan@boundlessgeo.com");
    }

    @Test
    public void NoBranchNameTest() {
        exception.expect(IllegalArgumentException.class);
        geogig.command(BranchRenameOp.class).call();
    }

    @Test
    public void InvalidBranchNameTest() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Component of ref cannot have two consecutive dots (..) anywhere.");
        geogig.command(BranchRenameOp.class).setNewName("ma..er").call();
    }

    @Test
    public void SameNameTest() {
        exception.expect(IllegalArgumentException.class);
        geogig.command(BranchRenameOp.class).setNewName("master").setOldName("master").call();
    }

    @Test
    public void RenamingABranchTest() throws Exception {
        insertAndAdd(points1);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).call();
        Ref TestBranch = geogig.command(BranchCreateOp.class).setName("TestBranch").call();

        Ref SuperTestBranch = geogig.command(BranchRenameOp.class).setOldName("TestBranch")
                .setNewName("SuperTestBranch").call();

        Optional<Ref> result = geogig.command(RefParse.class).setName("TestBranch").call();

        assertFalse(result.isPresent());

        result = geogig.command(RefParse.class).setName("SuperTestBranch").call();

        assertTrue(result.isPresent());

        assertEquals(TestBranch.getObjectId(), SuperTestBranch.getObjectId());
    }

    @Test
    public void RenamingCurrentBranchTest() throws Exception {
        insertAndAdd(points1);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).call();

        Ref NewMaster = geogig.command(BranchRenameOp.class).setOldName("master")
                .setNewName("newMaster").call();

        assertEquals(Ref.HEADS_PREFIX + "newMaster", NewMaster.getName());

        Optional<Ref> result = geogig.command(RefParse.class).setName("master").call();

        assertFalse(result.isPresent());

        result = geogig.command(RefParse.class).setName("newMaster").call();

        assertTrue(result.isPresent());

        result = geogig.command(RefParse.class).setName(Ref.HEAD).call();

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof SymRef);
        assertEquals(NewMaster.getName(), ((SymRef) result.get()).getTarget());
    }

    @Test
    public void RenamingUpdatesSymRefsTest() throws Exception {
        insertAndAdd(points1);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).call();
        Ref TestBranch = geogig.command(BranchCreateOp.class).setName("TestBranch").call();

        Optional<Ref> TestSymRef = geogig.command(UpdateSymRef.class)
                .setName(Ref.HEADS_PREFIX + "TestSymRef").setNewValue(TestBranch.getName()).call();

        assertTrue(TestSymRef.isPresent());
        assertEquals(TestBranch.getName(), ((SymRef) TestSymRef.get()).getTarget());

        Ref SuperTestBranch = geogig.command(BranchRenameOp.class).setOldName("TestBranch")
                .setNewName("SuperTestBranch").call();

        Optional<Ref> result = geogig.command(RefParse.class).setName("TestBranch").call();

        assertFalse(result.isPresent());

        result = geogig.command(RefParse.class).setName("SuperTestBranch").call();

        assertTrue(result.isPresent());

        result = geogig.command(RefParse.class).setName("TestSymRef").call();

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof SymRef);
        assertEquals(SuperTestBranch.getName(), ((SymRef) result.get()).getTarget());
    }

    @Test
    public void NoOldNameTest() throws Exception {
        insertAndAdd(points1);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).call();
        Ref TestBranch = geogig.command(BranchCreateOp.class).setName("TestBranch")
                .setAutoCheckout(true).call();

        Ref SuperTestBranch = geogig.command(BranchRenameOp.class).setNewName("SuperTestBranch")
                .call();

        Optional<Ref> result = geogig.command(RefParse.class).setName("TestBranch").call();

        assertFalse(result.isPresent());

        result = geogig.command(RefParse.class).setName("SuperTestBranch").call();

        assertTrue(result.isPresent());

        assertEquals(TestBranch.getObjectId(), SuperTestBranch.getObjectId());
    }

    @Test
    public void ForceRenameTest() throws Exception {
        insertAndAdd(points1);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).call();
        Ref TestBranch1 = geogig.command(BranchCreateOp.class).setName("TestBranch1").call();

        geogig.command(BranchCreateOp.class).setName("TestBranch2").setAutoCheckout(true).call();
        insertAndAdd(points2);
        geogig.command(AddOp.class).call();
        geogig.command(CommitOp.class).setMessage("this should be deleted").call();

        geogig.command(CheckoutOp.class).setSource("TestBranch1").call();

        Ref SuperTestBranch = geogig.command(BranchRenameOp.class).setNewName("TestBranch2")
                .setForce(true).call();

        Optional<Ref> result = geogig.command(RefParse.class).setName("TestBranch1").call();

        assertFalse(result.isPresent());

        result = geogig.command(RefParse.class).setName("TestBranch2").call();

        assertTrue(result.isPresent());

        assertEquals(TestBranch1.getObjectId(), SuperTestBranch.getObjectId());

        exception.expect(IllegalArgumentException.class);
        geogig.command(BranchRenameOp.class).setNewName("master").call();
    }
}
