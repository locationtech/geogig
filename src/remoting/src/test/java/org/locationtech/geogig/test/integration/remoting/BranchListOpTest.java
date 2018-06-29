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

import org.junit.Test;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.remotes.CloneOp;

import com.google.common.collect.ImmutableList;

public class BranchListOpTest extends RemoteRepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
        // Commit several features to the remote

        insertAndAdd(remoteGeogig.geogig, points1);
        remoteGeogig.geogig.command(CommitOp.class).call();

        // Create and checkout branch1
        remoteGeogig.geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("Branch1")
                .call();

        // Commit some changes to branch1
        insertAndAdd(remoteGeogig.geogig, points2);
        remoteGeogig.geogig.command(CommitOp.class).call();

        insertAndAdd(remoteGeogig.geogig, points3);
        remoteGeogig.geogig.command(CommitOp.class).call();

        // Checkout master and commit some changes
        remoteGeogig.geogig.command(CheckoutOp.class).setSource("master").call();

        insertAndAdd(remoteGeogig.geogig, lines1);
        remoteGeogig.geogig.command(CommitOp.class).call();

        insertAndAdd(remoteGeogig.geogig, lines2);
        remoteGeogig.geogig.command(CommitOp.class).call();

        // clone from the remote
        CloneOp clone = cloneOp();
        clone.setRemoteURI(remoteGeogig.envHome.toURI()).call();
    }

    @Test
    public void testBranchListOp() throws Exception {

        ImmutableList<Ref> branches = remoteGeogig.geogig.command(BranchListOp.class).setLocal(true)
                .setRemotes(false).call();

        assertEquals(Ref.HEADS_PREFIX + "Branch1", branches.get(0).getName());
        assertEquals(Ref.HEADS_PREFIX + "master", branches.get(1).getName());
    }

    @Test
    public void testRemoteListing() throws Exception {

        ImmutableList<Ref> branches = localGeogig.geogig.command(BranchListOp.class).setLocal(true)
                .setRemotes(true).call();

        assertEquals(Ref.HEADS_PREFIX + "Branch1", branches.get(0).getName());
        assertEquals(Ref.HEADS_PREFIX + "master", branches.get(1).getName());
        assertEquals(Ref.REMOTES_PREFIX + REMOTE_NAME + "/Branch1", branches.get(2).getName());
        assertEquals(Ref.REMOTES_PREFIX + REMOTE_NAME + "/master", branches.get(3).getName());
    }
}
