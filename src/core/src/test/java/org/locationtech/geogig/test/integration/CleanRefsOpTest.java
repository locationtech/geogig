/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.CleanRefsOp;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.porcelain.MergeOp;

public class CleanRefsOpTest extends RepositoryTestCase {

    protected @Override void setUpInternal() throws Exception {
    }

    @Test
    public void testClean() throws Exception {
        // Set up some refs to clean up
        repo.command(UpdateRefs.class).setReason("test initialization")//
                .add(Ref.MERGE_HEAD, ObjectId.NULL)//
                .add(Ref.ORIG_HEAD, ObjectId.NULL)//
                .add(Ref.CHERRY_PICK_HEAD, ObjectId.NULL)//
                .call();

        repo.context().blobStore().putBlob(MergeOp.MERGE_MSG, "Merge message".getBytes());

        List<String> cleanedUp = repo.command(CleanRefsOp.class).call();
        assertEquals(4, cleanedUp.size());
        assertTrue(cleanedUp.contains(Ref.MERGE_HEAD));
        assertTrue(cleanedUp.contains(Ref.ORIG_HEAD));
        assertTrue(cleanedUp.contains(Ref.CHERRY_PICK_HEAD));
        assertTrue(cleanedUp.contains(MergeOp.MERGE_MSG));

        Optional<Ref> ref = repo.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertFalse(ref.isPresent());
        ref = repo.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertFalse(ref.isPresent());
        ref = repo.command(RefParse.class).setName(Ref.CHERRY_PICK_HEAD).call();
        assertFalse(ref.isPresent());
        Optional<byte[]> mergeMsg = repo.context().blobStore().getBlob(MergeOp.MERGE_MSG);
        assertFalse(mergeMsg.isPresent());

        // Running it again should result in nothing being cleaned up.
        cleanedUp = repo.command(CleanRefsOp.class).call();

        assertEquals(0, cleanedUp.size());
    }

}
