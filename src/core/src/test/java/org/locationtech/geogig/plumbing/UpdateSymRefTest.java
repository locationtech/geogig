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
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Optional;

public class UpdateSymRefTest extends RepositoryTestCase {

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
        geogig.command(CommitOp.class).call();
        Ref branch = geogig.command(BranchCreateOp.class).setName("branch1").call();

        geogig.command(UpdateSymRef.class).setDelete(false).setName(Ref.HEAD)
                .setNewValue("refs/heads/branch1").setOldValue(Ref.MASTER)
                .setReason("this is a test").call();

        Optional<ObjectId> branchId = geogig.command(RevParse.class).setRefSpec(Ref.HEAD).call();
        assertTrue(branch.getObjectId().equals(branchId.get()));
    }

    @Test
    public void testNoName() {
        exception.expect(IllegalStateException.class);
        geogig.command(UpdateSymRef.class).call();
    }

    @Test
    public void testNoValue() {
        exception.expect(IllegalStateException.class);
        geogig.command(UpdateSymRef.class).setName(Ref.HEAD).call();
    }

    @Test
    public void testDeleteRefTurnedIntoASymbolicRef() throws Exception {
        insertAndAdd(points1);
        RevCommit commit = geogig.command(CommitOp.class).call();
        Ref branch = geogig.command(BranchCreateOp.class).setName("branch1").call();

        assertTrue(branch.getObjectId().equals(commit.getId()));

        geogig.command(UpdateSymRef.class).setName("refs/heads/branch1")
                .setOldValue(commit.getId().toString()).setNewValue(Ref.MASTER)
                .setReason("this is a test").call();

        Optional<Ref> branchId = geogig.command(RefParse.class).setName("refs/heads/branch1")
                .call();

        assertTrue(((SymRef) branchId.get()).getTarget().equals(Ref.MASTER));

        geogig.command(UpdateSymRef.class).setName("refs/heads/branch1").setDelete(true).call();
    }

    @Test
    public void testDeleteRefThatDoesNotExist() {
        Optional<Ref> test = geogig.command(UpdateSymRef.class).setName("NoRef").setDelete(true)
                .call();
        assertFalse(test.isPresent());
    }
}
