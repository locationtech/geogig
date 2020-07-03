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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.junit.Test;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.BranchRenameOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;

public class BranchRenameOpTest extends RepositoryTestCase {

    protected @Override void setUpInternal() throws Exception {
        repo.context().configDatabase().put("user.name", "groldan");
        repo.context().configDatabase().put("user.email", "groldan@test.com");
    }

    @Test
    public void NoBranchNameTest() {
        assertThrows(IllegalArgumentException.class, repo.command(BranchRenameOp.class)::call);
    }

    @Test
    public void InvalidBranchNameTest() {
        Exception e = assertThrows(IllegalArgumentException.class,
                repo.command(BranchRenameOp.class).setNewName("ma..er")::call);
        assertThat(e.getMessage(),
                containsString("Component of ref cannot have two consecutive dots (..) anywhere."));
    }

    @Test
    public void SameNameTest() {
        Exception e = assertThrows(IllegalArgumentException.class,
                repo.command(BranchRenameOp.class).setNewName("master").setOldName("master")::call);
    }

    @Test
    public void RenamingABranchTest() throws Exception {
        insertAndAdd(points1);
        repo.command(AddOp.class).call();
        repo.command(CommitOp.class).call();
        Ref TestBranch = repo.command(BranchCreateOp.class).setName("TestBranch").call();

        Ref SuperTestBranch = repo.command(BranchRenameOp.class).setOldName("TestBranch")
                .setNewName("SuperTestBranch").call();

        Optional<Ref> result = repo.command(RefParse.class).setName("TestBranch").call();

        assertFalse(result.isPresent());

        result = repo.command(RefParse.class).setName("SuperTestBranch").call();

        assertTrue(result.isPresent());

        assertEquals(TestBranch.getObjectId(), SuperTestBranch.getObjectId());
    }

    @Test
    public void RenamingCurrentBranchTest() throws Exception {
        insertAndAdd(points1);
        repo.command(AddOp.class).call();
        repo.command(CommitOp.class).call();

        Ref NewMaster = repo.command(BranchRenameOp.class).setOldName("master")
                .setNewName("newMaster").call();

        assertEquals(Ref.HEADS_PREFIX + "newMaster", NewMaster.getName());

        Optional<Ref> result = repo.command(RefParse.class).setName("master").call();

        assertFalse(result.isPresent());

        result = repo.command(RefParse.class).setName("newMaster").call();

        assertTrue(result.isPresent());

        result = repo.command(RefParse.class).setName(Ref.HEAD).call();

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof SymRef);
        assertEquals(NewMaster.getName(), ((SymRef) result.get()).getTarget());
    }

    @Test
    public void RenamingUpdatesSymRefsTest() throws Exception {
        insertAndAdd(points1);
        repo.command(AddOp.class).call();
        repo.command(CommitOp.class).call();
        Ref TestBranch = repo.command(BranchCreateOp.class).setName("TestBranch").call();

        Optional<Ref> TestSymRef = repo.command(UpdateSymRef.class).setReason("test setup")
                .setName(Ref.HEADS_PREFIX + "TestSymRef").setNewValue(TestBranch.getName()).call();

        assertTrue(TestSymRef.isPresent());
        assertEquals(TestBranch.getName(), ((SymRef) TestSymRef.get()).getTarget());

        Ref SuperTestBranch = repo.command(BranchRenameOp.class).setOldName("TestBranch")
                .setNewName("SuperTestBranch").call();

        Optional<Ref> result = repo.command(RefParse.class).setName("TestBranch").call();

        assertFalse(result.isPresent());

        result = repo.command(RefParse.class).setName("SuperTestBranch").call();

        assertTrue(result.isPresent());

        result = repo.command(RefParse.class).setName("TestSymRef").call();

        assertTrue(result.isPresent());
        assertTrue(result.get() instanceof SymRef);
        assertEquals(SuperTestBranch.getName(), ((SymRef) result.get()).getTarget());
    }

    @Test
    public void NoOldNameTest() throws Exception {
        insertAndAdd(points1);
        repo.command(AddOp.class).call();
        repo.command(CommitOp.class).call();
        Ref TestBranch = repo.command(BranchCreateOp.class).setName("TestBranch")
                .setAutoCheckout(true).call();

        Ref SuperTestBranch = repo.command(BranchRenameOp.class).setNewName("SuperTestBranch")
                .call();

        Optional<Ref> result = repo.command(RefParse.class).setName("TestBranch").call();

        assertFalse(result.isPresent());

        result = repo.command(RefParse.class).setName("SuperTestBranch").call();

        assertTrue(result.isPresent());

        assertEquals(TestBranch.getObjectId(), SuperTestBranch.getObjectId());
    }

    @Test
    public void ForceRenameTest() throws Exception {
        insertAndAdd(points1);
        repo.command(AddOp.class).call();
        repo.command(CommitOp.class).call();
        Ref TestBranch1 = repo.command(BranchCreateOp.class).setName("TestBranch1").call();

        repo.command(BranchCreateOp.class).setName("TestBranch2").setAutoCheckout(true).call();
        insertAndAdd(points2);
        repo.command(AddOp.class).call();
        repo.command(CommitOp.class).setMessage("this should be deleted").call();

        repo.command(CheckoutOp.class).setSource("TestBranch1").call();

        Ref SuperTestBranch = repo.command(BranchRenameOp.class).setNewName("TestBranch2")
                .setForce(true).call();

        Optional<Ref> result = repo.command(RefParse.class).setName("TestBranch1").call();

        assertFalse(result.isPresent());

        result = repo.command(RefParse.class).setName("TestBranch2").call();

        assertTrue(result.isPresent());

        assertEquals(TestBranch1.getObjectId(), SuperTestBranch.getObjectId());

        Exception e = assertThrows(IllegalArgumentException.class,
                repo.command(BranchRenameOp.class).setNewName("master")::call);
    }
}
