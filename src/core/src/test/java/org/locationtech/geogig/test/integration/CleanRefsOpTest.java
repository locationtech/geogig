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

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.CleanRefsOp;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.porcelain.MergeOp;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class CleanRefsOpTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Test
    public void testClean() throws Exception {
        // Set up some refs to clean up
        geogig.command(UpdateRef.class).setName(Ref.MERGE_HEAD).setNewValue(ObjectId.NULL).call();
        geogig.command(UpdateRef.class).setName(Ref.ORIG_HEAD).setNewValue(ObjectId.NULL).call();
        geogig.command(UpdateRef.class).setName(Ref.CHERRY_PICK_HEAD).setNewValue(ObjectId.NULL)
                .call();

        geogig.getRepository().blobStore().putBlob(MergeOp.MERGE_MSG, "Merge message".getBytes());

        ImmutableList<String> cleanedUp = geogig.command(CleanRefsOp.class).call();
        assertEquals(4, cleanedUp.size());
        assertTrue(cleanedUp.contains(Ref.MERGE_HEAD));
        assertTrue(cleanedUp.contains(Ref.ORIG_HEAD));
        assertTrue(cleanedUp.contains(Ref.CHERRY_PICK_HEAD));
        assertTrue(cleanedUp.contains(MergeOp.MERGE_MSG));

        Optional<Ref> ref = geogig.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertFalse(ref.isPresent());
        ref = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertFalse(ref.isPresent());
        ref = geogig.command(RefParse.class).setName(Ref.CHERRY_PICK_HEAD).call();
        assertFalse(ref.isPresent());
        Optional<byte[]> mergeMsg = geogig.getRepository().blobStore().getBlob(MergeOp.MERGE_MSG);
        assertFalse(mergeMsg.isPresent());

        // Running it again should result in nothing being cleaned up.
        cleanedUp = geogig.command(CleanRefsOp.class).call();

        assertEquals(0, cleanedUp.size());
    }

}
