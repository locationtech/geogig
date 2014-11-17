/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.test.integration;

import static org.locationtech.geogig.api.NodeRef.appendChild;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.CheckoutOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.ConfigOp;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.api.porcelain.MergeConflictsException;
import org.locationtech.geogig.api.porcelain.MergeOp;
import org.locationtech.geogig.api.porcelain.ResetOp;
import org.locationtech.geogig.api.porcelain.ResetOp.ResetMode;
import org.opengis.feature.Feature;

import com.google.common.base.Optional;
import com.google.common.base.Suppliers;

public class ResetOpTest extends RepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        // These values should be used during a commit to set author/committer
        // TODO: author/committer roles need to be defined better, but for
        // now they are the same thing.
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue("groldan").call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue("groldan@opengeo.org").call();
    }

    @Test
    public void testResetAllMixed() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertEquals(oId2, repo.index().findStaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(oId3, repo.index().findStaged(appendChild(pointsName, idP3)).get()
                .getObjectId());

        geogig.command(ResetOp.class).call();

        assertEquals(oId1, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertFalse(repo.index().findStaged(appendChild(pointsName, idP2)).isPresent());
        assertFalse(repo.index().findStaged(appendChild(pointsName, idP3)).isPresent());

        assertEquals(oId1_modified,
                repo.workingTree().findUnstaged(appendChild(pointsName, idP1)).get()
                        .getObjectId());
        assertEquals(oId2, repo.workingTree().findUnstaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(oId3, repo.workingTree().findUnstaged(appendChild(pointsName, idP3)).get()
                .getObjectId());

    }

    @Test
    public void testResetPoints() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);
        ObjectId oId4 = insertAndAdd(lines1);

        assertEquals(oId1_modified, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertEquals(oId2, repo.index().findStaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(oId3, repo.index().findStaged(appendChild(pointsName, idP3)).get()
                .getObjectId());
        assertEquals(oId4, repo.index().findStaged(appendChild(linesName, idL1)).get()
                .getObjectId());

        geogig.command(ResetOp.class).addPattern(pointsName).call();

        assertEquals(oId1, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertFalse(repo.index().findStaged(appendChild(pointsName, idP2)).isPresent());
        assertFalse(repo.index().findStaged(appendChild(pointsName, idP3)).isPresent());
        assertTrue(repo.index().findStaged(appendChild(linesName, idL1)).isPresent());

        assertEquals(oId1_modified,
                repo.workingTree().findUnstaged(appendChild(pointsName, idP1)).get()
                        .getObjectId());
        assertEquals(oId2, repo.workingTree().findUnstaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(oId3, repo.workingTree().findUnstaged(appendChild(pointsName, idP3)).get()
                .getObjectId());
        assertEquals(oId4, repo.workingTree().findUnstaged(appendChild(linesName, idL1)).get()
                .getObjectId());

    }

    @Test
    public void testResetSingle() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertEquals(oId2, repo.index().findStaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(oId3, repo.index().findStaged(appendChild(pointsName, idP3)).get()
                .getObjectId());

        geogig.command(ResetOp.class).addPattern(appendChild(pointsName, idP2)).call();

        assertEquals(oId1_modified, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertFalse(repo.index().findStaged(appendChild(pointsName, idP2)).isPresent());
        assertTrue(repo.index().findStaged(appendChild(pointsName, idP3)).isPresent());

        assertEquals(oId1_modified,
                repo.workingTree().findUnstaged(appendChild(pointsName, idP1)).get()
                        .getObjectId());
        assertEquals(oId2, repo.workingTree().findUnstaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(oId3, repo.workingTree().findUnstaged(appendChild(pointsName, idP3)).get()
                .getObjectId());
    }

    @Test
    public void testResetHard() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertEquals(oId2, repo.index().findStaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(oId3, repo.index().findStaged(appendChild(pointsName, idP3)).get()
                .getObjectId());

        geogig.command(ResetOp.class).setMode(ResetMode.HARD).call();

        assertEquals(oId1, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertFalse(repo.index().findStaged(appendChild(pointsName, idP2)).isPresent());
        assertFalse(repo.index().findStaged(appendChild(pointsName, idP3)).isPresent());

        assertEquals(oId1, repo.workingTree().findUnstaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertFalse(repo.workingTree().findUnstaged(appendChild(pointsName, idP2)).isPresent());
        assertFalse(repo.workingTree().findUnstaged(appendChild(pointsName, idP3)).isPresent());

    }

    @Test
    public void testResetSoft() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertEquals(oId2, repo.index().findStaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(oId3, repo.index().findStaged(appendChild(pointsName, idP3)).get()
                .getObjectId());

        final Optional<Ref> currHead = geogig.command(RefParse.class).setName(Ref.HEAD).call();

        geogig.command(ResetOp.class).setCommit(Suppliers.ofInstance(currHead.get().getObjectId()))
                .setMode(ResetMode.SOFT).call();

        assertEquals(oId1_modified, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertEquals(oId2, repo.index().findStaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(oId3, repo.index().findStaged(appendChild(pointsName, idP3)).get()
                .getObjectId());

        assertEquals(oId1_modified,
                repo.workingTree().findUnstaged(appendChild(pointsName, idP1)).get()
                        .getObjectId());
        assertEquals(oId2, repo.workingTree().findUnstaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(oId3, repo.workingTree().findUnstaged(appendChild(pointsName, idP3)).get()
                .getObjectId());

    }

    @Test
    public void testResetModePlusPatterns() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertEquals(oId2, repo.index().findStaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(oId3, repo.index().findStaged(appendChild(pointsName, idP3)).get()
                .getObjectId());

        exception.expect(IllegalStateException.class);
        geogig.command(ResetOp.class).addPattern(pointsName).setMode(ResetMode.SOFT).call();

    }

    @Test
    public void testResetMerge() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertEquals(oId2, repo.index().findStaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(oId3, repo.index().findStaged(appendChild(pointsName, idP3)).get()
                .getObjectId());

        exception.expect(UnsupportedOperationException.class);
        geogig.command(ResetOp.class).setMode(ResetMode.MERGE).call();

    }

    @Test
    public void testResetKeep() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        assertEquals(oId1, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());

        ObjectId oId1_modified = insertAndAdd(points1_modified);
        ObjectId oId2 = insertAndAdd(points2);
        ObjectId oId3 = insertAndAdd(points3);

        assertEquals(oId1_modified, repo.index().findStaged(appendChild(pointsName, idP1)).get()
                .getObjectId());
        assertEquals(oId2, repo.index().findStaged(appendChild(pointsName, idP2)).get()
                .getObjectId());
        assertEquals(oId3, repo.index().findStaged(appendChild(pointsName, idP3)).get()
                .getObjectId());

        exception.expect(UnsupportedOperationException.class);
        geogig.command(ResetOp.class).setCommit(null).setMode(ResetMode.KEEP).call();

    }

    @Test
    public void testResetNoCommits() throws Exception {
        exception.expect(IllegalStateException.class);
        geogig.command(ResetOp.class).call();

    }

    @Test
    public void testEnum() throws Exception {
        ResetMode.values();
        assertEquals(ResetMode.valueOf("HARD"), ResetMode.HARD);
    }

    @Test
    public void testResetFixesConflict() throws Exception {
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1);
        RevCommit resetCommit = geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        geogig.command(CommitOp.class).call();

        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        try {
            geogig.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch.getObjectId()))
                    .call();
            fail();
        } catch (MergeConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        geogig.command(ResetOp.class).setMode(ResetMode.HARD)
                .setCommit(Suppliers.ofInstance(resetCommit.getId())).call();
        List<Conflict> conflicts = geogig.getRepository().stagingDatabase()
                .getConflicts(null, null);
        assertTrue(conflicts.isEmpty());
        Optional<Ref> ref = geogig.command(RefParse.class).setName(Ref.MERGE_HEAD).call();
        assertFalse(ref.isPresent());
    }

    @Test
    public void testResetPathFixesConflict() throws Exception {
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1);
        RevCommit resetCommit = geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        geogig.command(CommitOp.class).call();

        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        try {
            geogig.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch.getObjectId()))
                    .call();
            fail();
        } catch (MergeConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        geogig.command(ResetOp.class).addPattern(pointsName + "/" + idP1)
                .setCommit(Suppliers.ofInstance(resetCommit.getId())).call();
        List<Conflict> conflicts = geogig.getRepository().stagingDatabase()
                .getConflicts(null, null);
        assertTrue(conflicts.isEmpty());
    }

    @Test
    public void testResetPathToHeadVersionFixesConflict() throws Exception {
        Feature points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
        Feature points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        geogig.command(CommitOp.class).call();

        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        try {
            geogig.command(MergeOp.class).addCommit(Suppliers.ofInstance(branch.getObjectId()))
                    .call();
            fail();
        } catch (MergeConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }

        geogig.command(ResetOp.class).addPattern(pointsName + "/" + idP1).call();
        List<Conflict> conflicts = geogig.getRepository().stagingDatabase()
                .getConflicts(null, null);
        assertTrue(conflicts.isEmpty());
    }
}
