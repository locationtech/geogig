/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.model.NodeRef.appendChild;

import java.util.Optional;

import org.junit.Test;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.ResetOp;
import org.locationtech.geogig.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.ConflictsDatabase;

public class ResetOpTest extends RepositoryTestCase {

    protected @Override void setUpInternal() throws Exception {
        // These values should be used during a commit to set author/committer
        // TODO: author/committer roles need to be defined better, but for
        // now they are the same thing.
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue("groldan").call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue("groldan@test.com").call();
    }

    @Test
    public void testResetAllMixed() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        repo.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.context().stagingArea().findStaged(appendChild(pointsName, idP1))
                .get().getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.context().stagingArea()
                .findStaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, repo.context().stagingArea().findStaged(appendChild(pointsName, idP2))
                .get().getObjectId());
        assertEquals(oId3, repo.context().stagingArea().findStaged(appendChild(pointsName, idP3))
                .get().getObjectId());

        repo.command(ResetOp.class).call();

        assertEquals(oId1, repo.context().stagingArea().findStaged(appendChild(pointsName, idP1))
                .get().getObjectId());
        assertFalse(
                repo.context().stagingArea().findStaged(appendChild(pointsName, idP2)).isPresent());
        assertFalse(
                repo.context().stagingArea().findStaged(appendChild(pointsName, idP3)).isPresent());

        assertEquals(oId1_modified, repo.context().workingTree()
                .findUnstaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, repo.context().workingTree().findUnstaged(appendChild(pointsName, idP2))
                .get().getObjectId());
        assertEquals(oId3, repo.context().workingTree().findUnstaged(appendChild(pointsName, idP3))
                .get().getObjectId());

    }

    @Test
    public void testResetPoints() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        repo.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.context().stagingArea().findStaged(appendChild(pointsName, idP1))
                .get().getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);
        ObjectId oId4 = insertAndAdd(lines1);

        assertEquals(oId1_modified, repo.context().stagingArea()
                .findStaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, repo.context().stagingArea().findStaged(appendChild(pointsName, idP2))
                .get().getObjectId());
        assertEquals(oId3, repo.context().stagingArea().findStaged(appendChild(pointsName, idP3))
                .get().getObjectId());
        assertEquals(oId4, repo.context().stagingArea().findStaged(appendChild(linesName, idL1))
                .get().getObjectId());

        repo.command(ResetOp.class).addPattern(pointsName).call();

        assertEquals(oId1, repo.context().stagingArea().findStaged(appendChild(pointsName, idP1))
                .get().getObjectId());
        assertFalse(
                repo.context().stagingArea().findStaged(appendChild(pointsName, idP2)).isPresent());
        assertFalse(
                repo.context().stagingArea().findStaged(appendChild(pointsName, idP3)).isPresent());
        assertTrue(
                repo.context().stagingArea().findStaged(appendChild(linesName, idL1)).isPresent());

        assertEquals(oId1_modified, repo.context().workingTree()
                .findUnstaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, repo.context().workingTree().findUnstaged(appendChild(pointsName, idP2))
                .get().getObjectId());
        assertEquals(oId3, repo.context().workingTree().findUnstaged(appendChild(pointsName, idP3))
                .get().getObjectId());
        assertEquals(oId4, repo.context().workingTree().findUnstaged(appendChild(linesName, idL1))
                .get().getObjectId());

    }

    @Test
    public void testResetSingle() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        repo.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.context().stagingArea().findStaged(appendChild(pointsName, idP1))
                .get().getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.context().stagingArea()
                .findStaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, repo.context().stagingArea().findStaged(appendChild(pointsName, idP2))
                .get().getObjectId());
        assertEquals(oId3, repo.context().stagingArea().findStaged(appendChild(pointsName, idP3))
                .get().getObjectId());

        repo.command(ResetOp.class).addPattern(appendChild(pointsName, idP2)).call();

        assertEquals(oId1_modified, repo.context().stagingArea()
                .findStaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertFalse(
                repo.context().stagingArea().findStaged(appendChild(pointsName, idP2)).isPresent());
        assertTrue(
                repo.context().stagingArea().findStaged(appendChild(pointsName, idP3)).isPresent());

        assertEquals(oId1_modified, repo.context().workingTree()
                .findUnstaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, repo.context().workingTree().findUnstaged(appendChild(pointsName, idP2))
                .get().getObjectId());
        assertEquals(oId3, repo.context().workingTree().findUnstaged(appendChild(pointsName, idP3))
                .get().getObjectId());
    }

    @Test
    public void testResetHard() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        repo.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.context().stagingArea().findStaged(appendChild(pointsName, idP1))
                .get().getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.context().stagingArea()
                .findStaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, repo.context().stagingArea().findStaged(appendChild(pointsName, idP2))
                .get().getObjectId());
        assertEquals(oId3, repo.context().stagingArea().findStaged(appendChild(pointsName, idP3))
                .get().getObjectId());

        repo.command(ResetOp.class).setMode(ResetMode.HARD).call();

        assertEquals(oId1, repo.context().stagingArea().findStaged(appendChild(pointsName, idP1))
                .get().getObjectId());
        assertFalse(
                repo.context().stagingArea().findStaged(appendChild(pointsName, idP2)).isPresent());
        assertFalse(
                repo.context().stagingArea().findStaged(appendChild(pointsName, idP3)).isPresent());

        assertEquals(oId1, repo.context().workingTree().findUnstaged(appendChild(pointsName, idP1))
                .get().getObjectId());
        assertFalse(repo.context().workingTree().findUnstaged(appendChild(pointsName, idP2))
                .isPresent());
        assertFalse(repo.context().workingTree().findUnstaged(appendChild(pointsName, idP3))
                .isPresent());

    }

    @Test
    public void testResetSoft() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        repo.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.context().stagingArea().findStaged(appendChild(pointsName, idP1))
                .get().getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.context().stagingArea()
                .findStaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, repo.context().stagingArea().findStaged(appendChild(pointsName, idP2))
                .get().getObjectId());
        assertEquals(oId3, repo.context().stagingArea().findStaged(appendChild(pointsName, idP3))
                .get().getObjectId());

        final Optional<Ref> currHead = repo.command(RefParse.class).setName(Ref.HEAD).call();

        repo.command(ResetOp.class).setCommit(currHead.get()::getObjectId).setMode(ResetMode.SOFT)
                .call();

        assertEquals(oId1_modified, repo.context().stagingArea()
                .findStaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, repo.context().stagingArea().findStaged(appendChild(pointsName, idP2))
                .get().getObjectId());
        assertEquals(oId3, repo.context().stagingArea().findStaged(appendChild(pointsName, idP3))
                .get().getObjectId());

        assertEquals(oId1_modified, repo.context().workingTree()
                .findUnstaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, repo.context().workingTree().findUnstaged(appendChild(pointsName, idP2))
                .get().getObjectId());
        assertEquals(oId3, repo.context().workingTree().findUnstaged(appendChild(pointsName, idP3))
                .get().getObjectId());

    }

    @Test
    public void testResetModePlusPatterns() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        repo.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.context().stagingArea().findStaged(appendChild(pointsName, idP1))
                .get().getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.context().stagingArea()
                .findStaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, repo.context().stagingArea().findStaged(appendChild(pointsName, idP2))
                .get().getObjectId());
        assertEquals(oId3, repo.context().stagingArea().findStaged(appendChild(pointsName, idP3))
                .get().getObjectId());

        assertThrows(IllegalArgumentException.class,
                repo.command(ResetOp.class).addPattern(pointsName).setMode(ResetMode.SOFT)::call);
    }

    @Test
    public void testResetMerge() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        repo.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.context().stagingArea().findStaged(appendChild(pointsName, idP1))
                .get().getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.context().stagingArea()
                .findStaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, repo.context().stagingArea().findStaged(appendChild(pointsName, idP2))
                .get().getObjectId());
        assertEquals(oId3, repo.context().stagingArea().findStaged(appendChild(pointsName, idP3))
                .get().getObjectId());

        assertThrows(UnsupportedOperationException.class,
                repo.command(ResetOp.class).setMode(ResetMode.MERGE)::call);

    }

    @Test
    public void testResetKeep() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        repo.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.context().stagingArea().findStaged(appendChild(pointsName, idP1))
                .get().getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.context().stagingArea()
                .findStaged(appendChild(pointsName, idP1)).get().getObjectId());
        assertEquals(oId2, repo.context().stagingArea().findStaged(appendChild(pointsName, idP2))
                .get().getObjectId());
        assertEquals(oId3, repo.context().stagingArea().findStaged(appendChild(pointsName, idP3))
                .get().getObjectId());

        assertThrows(UnsupportedOperationException.class, repo.command(ResetOp.class)
                .setCommit((ObjectId) null).setMode(ResetMode.KEEP)::call);

    }

    @Test
    public void testResetNoCommits() throws Exception {
        assertThrows(IllegalArgumentException.class, repo.command(ResetOp.class)::call);
    }

    @Test
    public void testEnum() throws Exception {
        ResetMode.values();
        assertEquals(ResetMode.valueOf("HARD"), ResetMode.HARD);
    }

    @Test
    public void testResetFixesConflict() throws Exception {
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", Integer.valueOf(1000),
                "POINT(1 1)");
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", Integer.valueOf(2000),
                "POINT(1 1)");
        insertAndAdd(points1);
        RevCommit resetCommit = repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points1Modified);
        repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        repo.command(CommitOp.class).call();

        repo.command(CheckoutOp.class).setSource("master").call();
        Ref branch = repo.command(RefParse.class).setName("TestBranch").call().get();
        try {
            repo.command(MergeOp.class).addCommit(branch.getObjectId())
                    .setMessage("Merge features.").call();
            fail();
        } catch (MergeConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        repo.command(ResetOp.class).setMode(ResetMode.HARD).setCommit(resetCommit::getId).call();
        Repository repository = repo;
        assertEquals(0, repository.context().conflictsDatabase().getCountByPrefix(null, null));
        Optional<Ref> ref = repo.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertFalse(ref.isPresent());
        ref = repo.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertFalse(ref.isPresent());
        Optional<byte[]> mergemsg = repository.context().blobStore().getBlob(MergeOp.MERGE_MSG);
        assertFalse(mergemsg.isPresent());
    }

    @Test
    public void testResetPathFixesConflict() throws Exception {
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", Integer.valueOf(1000),
                "POINT(1 1)");
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", Integer.valueOf(2000),
                "POINT(1 1)");
        insertAndAdd(points1);
        RevCommit resetCommit = repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points1Modified);
        repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        repo.command(CommitOp.class).call();

        repo.command(CheckoutOp.class).setSource("master").call();
        Ref branch = repo.command(RefParse.class).setName("TestBranch").call().get();
        try {
            repo.command(MergeOp.class).addCommit(branch.getObjectId()).call();
            fail();
        } catch (MergeConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        repo.command(ResetOp.class).addPattern(pointsName + "/" + idP1)
                .setCommit(resetCommit::getId).call();
        Repository repository = repo;
        ConflictsDatabase conflicts = repository.context().conflictsDatabase();
        assertEquals(0, conflicts.getCountByPrefix(null, null));
    }

    @Test
    public void testResetPathToHeadVersionFixesConflict() throws Exception {
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", Integer.valueOf(1000),
                "POINT(1 1)");
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", Integer.valueOf(2000),
                "POINT(1 1)");
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        repo.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points1Modified);
        repo.command(CommitOp.class).call();
        repo.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        repo.command(CommitOp.class).call();

        repo.command(CheckoutOp.class).setSource("master").call();
        Ref branch = repo.command(RefParse.class).setName("TestBranch").call().get();
        try {
            repo.command(MergeOp.class).addCommit(branch.getObjectId()).call();
            fail();
        } catch (MergeConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        repo.command(ResetOp.class).addPattern(pointsName + "/" + idP1).call();
        Repository repository = repo;
        ConflictsDatabase conflicts = repository.context().conflictsDatabase();
        assertEquals(0, conflicts.getCountByPrefix(null, null));
    }
}
