/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Optional;

public class UpdateRefTest extends RepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        injector.configDatabase().put("user.name", "groldan");
        injector.configDatabase().put("user.email", "groldan@boundlessgeo.com");
    }

    @Test
    public void testConstructorAndMutators() throws Exception {
        insertAndAdd(points1);
        RevCommit commit1 = geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();

        insertAndAdd(points2);
        RevCommit commit2 = geogig.command(CommitOp.class).call();
        Optional<Ref> newBranch = geogig.command(UpdateRef.class).setName("refs/heads/branch1")
                .setNewValue(commit2.getId()).setOldValue(commit1.getId()).setReason("Testing")
                .call();

        assertTrue(newBranch.get().getObjectId().equals(commit2.getId()));
        assertFalse(newBranch.get().getObjectId().equals(commit1.getId()));
    }

    @Test
    public void testNoName() {
        exception.expect(IllegalStateException.class);
        geogig.command(UpdateRef.class).call();
    }

    @Test
    public void testNoValue() {
        exception.expect(IllegalStateException.class);
        geogig.command(UpdateRef.class).setName(Ref.MASTER).call();
    }

    @Test
    public void testDeleteRefThatWasASymRef() throws Exception {
        insertAndAdd(points1);
        RevCommit commit1 = geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();

        insertAndAdd(points2);
        RevCommit commit2 = geogig.command(CommitOp.class).call();

        geogig.command(UpdateSymRef.class).setName("refs/heads/branch1")
                .setOldValue(commit1.getId().toString()).setNewValue(Ref.MASTER)
                .setReason("this is a test").call();

        geogig.command(UpdateRef.class).setName("refs/heads/branch1").setNewValue(commit2.getId())
                .setOldValue(Ref.MASTER).call();

        Optional<Ref> branchId = geogig.command(RefParse.class).setName("refs/heads/branch1")
                .call();

        assertTrue(branchId.get().getObjectId().equals(commit2.getId()));

        geogig.command(UpdateRef.class).setDelete(true).setName("refs/heads/branch1").call();
    }

    @Test
    public void testDeleteWithNonexistentName() {
        Optional<Ref> ref = geogig.command(UpdateRef.class).setDelete(true).setName("NoRef").call();
        assertFalse(ref.isPresent());
    }
}
