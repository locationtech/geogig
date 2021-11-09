/* Copyright (c) 2013-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration.remoting;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.remotes.CloneOp;

public class BranchListOpTest extends RemoteRepositoryTestCase {

    protected @Override void setUpInternal() throws Exception {
        // Commit several features to the remote

        insertAndAdd(originRepo, points1);
        originRepo.command(CommitOp.class).call();

        // Create and checkout branch1
        originRepo.command(BranchCreateOp.class).setAutoCheckout(true).setName("Branch1").call();

        // Commit some changes to branch1
        insertAndAdd(originRepo, points2);
        originRepo.command(CommitOp.class).call();

        insertAndAdd(originRepo, points3);
        originRepo.command(CommitOp.class).call();

        // Checkout master and commit some changes
        originRepo.command(CheckoutOp.class).setSource("master").call();

        insertAndAdd(originRepo, lines1);
        originRepo.command(CommitOp.class).call();

        insertAndAdd(originRepo, lines2);
        originRepo.command(CommitOp.class).call();

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setRemoteURI(originRepo.getLocation()).call();
    }

    @Test
    public void testBranchListOp() throws Exception {

        List<Ref> branches = originRepo.command(BranchListOp.class).setLocal(true).setRemotes(false)
                .call();

        assertEquals(Ref.HEADS_PREFIX + "Branch1", branches.get(0).getName());
        assertEquals(Ref.HEADS_PREFIX + "master", branches.get(1).getName());
    }

    @Test
    public void testRemoteListing() throws Exception {

        List<Ref> branches = localRepo.command(BranchListOp.class).setLocal(true).setRemotes(true)
                .call();

        assertEquals(Ref.HEADS_PREFIX + "Branch1", branches.get(0).getName());
        assertEquals(Ref.HEADS_PREFIX + "master", branches.get(1).getName());
        assertEquals(Ref.REMOTES_PREFIX + REMOTE_NAME + "/Branch1", branches.get(2).getName());
        assertEquals(Ref.REMOTES_PREFIX + REMOTE_NAME + "/master", branches.get(3).getName());
    }
}
