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

import java.util.Iterator;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.merge.ConflictsQueryOp;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.RevertConflictsException;
import org.locationtech.geogig.porcelain.RevertOp;
import org.locationtech.geogig.repository.Conflict;
import org.opengis.feature.Feature;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

public class RevertOpTest extends RepositoryTestCase {
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
                .setValue("groldan@boundlessgeo.com").call();
    }

    @Test
    public void testRevert() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        insertAndAdd(points2);
        RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        insertAndAdd(points1_modified);
        RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for modified " + idP1)
                .call();

        ObjectId oId3 = insertAndAdd(points3);
        RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();

        deleteAndAdd(points3);
        RevCommit c5 = geogig.command(CommitOp.class).setMessage("commit for deleted " + idP3)
                .call();

        // revert Points.2 add, Points.1 change, and Points.3 delete
        geogig.command(RevertOp.class).addCommit(c2.getId()).addCommit(c3.getId())
                .addCommit(c5.getId()).call();

        final Optional<Ref> currHead = geogig.command(RefParse.class).setName(Ref.HEAD).call();

        final Optional<ObjectId> headTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish(currHead.get().getObjectId()).call();

        RevTree headTree = repo.getTree(headTreeId.get());

        Optional<NodeRef> points1Node = geogig.command(FindTreeChild.class)
                .setChildPath(NodeRef.appendChild(pointsName, idP1)).setParent(headTree).call();

        assertTrue(points1Node.isPresent());
        assertEquals(oId1, points1Node.get().getNode().getObjectId());

        Optional<NodeRef> points2Node = geogig.command(FindTreeChild.class)
                .setChildPath(NodeRef.appendChild(pointsName, idP2)).setParent(headTree).call();

        assertFalse(points2Node.isPresent());

        Optional<NodeRef> points3Node = geogig.command(FindTreeChild.class)
                .setChildPath(NodeRef.appendChild(pointsName, idP3)).setParent(headTree).call();

        assertTrue(points3Node.isPresent());
        assertEquals(oId3, points3Node.get().getNode().getObjectId());

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();

        // There should be 3 new commits, followed by all of the previous commits.
        log.next();
        log.next();
        log.next();

        assertEquals(c5.getId(), log.next().getId());
        assertEquals(c4.getId(), log.next().getId());
        assertEquals(c3.getId(), log.next().getId());
        assertEquals(c2.getId(), log.next().getId());
        assertEquals(c1.getId(), log.next().getId());
    }

    @Test
    public void testRevertWithoutCommit() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        insertAndAdd(points2);
        RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        insertAndAdd(points1_modified);
        RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for modified " + idP1)
                .call();

        ObjectId oId3 = insertAndAdd(points3);
        RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();

        deleteAndAdd(points3);
        RevCommit c5 = geogig.command(CommitOp.class).setMessage("commit for deleted " + idP3)
                .call();

        // revert Points.2 add, Points.1 change, and Points.3 delete
        geogig.command(RevertOp.class).addCommit(c2.getId()).addCommit(c3.getId())
                .addCommit(c5.getId()).setCreateCommit(false).call();

        final Optional<Ref> currHead = geogig.command(RefParse.class).setName(Ref.WORK_HEAD).call();

        final Optional<ObjectId> headTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish(currHead.get().getObjectId()).call();

        RevTree headTree = repo.getTree(headTreeId.get());

        Optional<NodeRef> points1Node = geogig.command(FindTreeChild.class)
                .setChildPath(NodeRef.appendChild(pointsName, idP1)).setParent(headTree).call();

        assertTrue(points1Node.isPresent());
        assertEquals(oId1, points1Node.get().getNode().getObjectId());

        Optional<NodeRef> points2Node = geogig.command(FindTreeChild.class)
                .setChildPath(NodeRef.appendChild(pointsName, idP2)).setParent(headTree).call();

        assertFalse(points2Node.isPresent());

        Optional<NodeRef> points3Node = geogig.command(FindTreeChild.class)
                .setChildPath(NodeRef.appendChild(pointsName, idP3)).setParent(headTree).call();

        assertTrue(points3Node.isPresent());
        assertEquals(oId3, points3Node.get().getNode().getObjectId());

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();

        // There should only the old commits.

        assertEquals(c5.getId(), log.next().getId());
        assertEquals(c4.getId(), log.next().getId());
        assertEquals(c3.getId(), log.next().getId());
        assertEquals(c2.getId(), log.next().getId());
        assertEquals(c1.getId(), log.next().getId());
    }

    @Test
    public void testHeadWithNoHistory() throws Exception {
        exception.expect(IllegalStateException.class);
        geogig.command(RevertOp.class).call();
    }

    @Test
    public void testUncleanWorkingTree() throws Exception {
        insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        insert(points2);
        exception.expect(IllegalStateException.class);
        geogig.command(RevertOp.class).addCommit(c1.getId()).call();
    }

    @Test
    public void testUncleanIndex() throws Exception {
        insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        insertAndAdd(points2);
        exception.expect(IllegalStateException.class);
        geogig.command(RevertOp.class).addCommit(c1.getId()).call();
    }

    @Test
    public void testRevertOnlyCommit() throws Exception {
        insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        geogig.command(RevertOp.class).addCommit(c1.getId()).call();

        final Optional<Ref> currHead = geogig.command(RefParse.class).setName(Ref.HEAD).call();

        final Optional<ObjectId> headTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish(currHead.get().getObjectId()).call();

        RevTree headTree = repo.getTree(headTreeId.get());

        Optional<NodeRef> points1Node = geogig.command(FindTreeChild.class)
                .setChildPath(NodeRef.appendChild(pointsName, idP1)).setParent(headTree).call();

        assertFalse(points1Node.isPresent());
    }

    @Test
    public void testNoUserNameForResolveCommiter() throws Exception {
        insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue(null).call();
        exception.expect(IllegalStateException.class);
        geogig.command(RevertOp.class).addCommit(c1.getId()).call();
    }

    @Test
    public void testNoUserEmailForResolveCommiterEmail() throws Exception {
        insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue(null).call();
        exception.expect(IllegalStateException.class);
        geogig.command(RevertOp.class).addCommit(c1.getId()).call();
    }

    @Test
    public void testRevertToWrongCommit() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        exception.expect(IllegalArgumentException.class);
        geogig.command(RevertOp.class).addCommit(RevObjectTestSupport.hashString("wrong")).call();
    }

    @Test
    public void testRevertUsingContinueAndAbort() throws Exception {
        insertAndAdd(points1);
        RevCommit commit = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        exception.expect(IllegalArgumentException.class);
        geogig.command(RevertOp.class).addCommit(commit.getId()).setAbort(true).setContinue(true)
                .call();
    }

    @Test
    public void testStillDeletedMergeConflictResolution() throws Exception {
        insertAndAdd(points1);
        @SuppressWarnings("unused")
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        deleteAndAdd(points1);
        RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for removing " + idP1)
                .call();
        @SuppressWarnings("unused")
        ObjectId oId1 = insertAndAdd(points1);
        @SuppressWarnings("unused")
        RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP1 + " again")
                .call();
        try {
            geogig.command(RevertOp.class).addCommit(c2.getId()).call();
            fail();
        } catch (RevertConflictsException e) {
            assertTrue(e.getMessage().contains(idP1));
        }

    }

    @Test
    public void testRevertToSameFeatureIsNotConflict() throws Exception {
        @SuppressWarnings("unused")
        ObjectId oId1 = insertAndAdd(points1);
        @SuppressWarnings("unused")
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        insertAndAdd(points1_modified);
        RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for modified " + idP1)
                .call();
        insertAndAdd(points1);
        @SuppressWarnings("unused")
        RevCommit c3 = geogig.command(CommitOp.class)
                .setMessage("commit for modified " + idP1 + " again").call();

        geogig.command(RevertOp.class).addCommit(c2.getId()).call();

    }

    @Test
    public void testRevertModifiedFeatureConflictSolveAndContinue() throws Exception {
        ObjectId oId1 = insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        insertAndAdd(points1_modified);
        RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for modified " + idP1)
                .call();
        Feature points1_modifiedB = feature(pointsType, idP1, "StringProp1_2", new Integer(2000),
                "POINT(1 1)");
        insertAndAdd(points1_modifiedB);
        RevCommit c3 = geogig.command(CommitOp.class)
                .setMessage("commit for modified " + idP1 + " again").call();
        try {
            geogig.command(RevertOp.class).addCommit(c2.getId()).call();
            fail();
        } catch (RevertConflictsException e) {
            assertTrue(e.getMessage().contains(idP1));
        }

        Optional<Ref> ref = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertTrue(ref.isPresent());
        assertEquals(c3.getId(), ref.get().getObjectId());

        List<Conflict> conflicts = Lists
                .newArrayList(geogig.command(ConflictsQueryOp.class).call());
        assertEquals(1, conflicts.size());
        String path = NodeRef.appendChild(pointsName, idP1);
        assertEquals(conflicts.get(0).getPath(), path);
        assertEquals(conflicts.get(0).getOurs(),
                RevFeature.builder().build(points1_modifiedB).getId());
        assertEquals(conflicts.get(0).getTheirs(), RevFeature.builder().build(points1).getId());

        // solve, and continue
        insert(points1);
        geogig.command(AddOp.class).call();
        geogig.command(RevertOp.class).setContinue(true).call();

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        RevCommit logCommit = log.next();
        assertEquals(c2.getAuthor().getName(), logCommit.getAuthor().getName());
        assertEquals(c2.getCommitter().getName(), logCommit.getCommitter().getName());
        assertEquals("Revert '" + c2.getMessage() + "'\nThis reverts " + c2.getId().toString(),
                logCommit.getMessage());
        assertNotSame(c2.getCommitter().getTimestamp(), logCommit.getCommitter().getTimestamp());
        assertNotSame(c2.getTreeId(), logCommit.getTreeId());

        final Optional<Ref> currHead = geogig.command(RefParse.class).setName(Ref.HEAD).call();

        final Optional<ObjectId> headTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish(currHead.get().getObjectId()).call();
        RevTree headTree = repo.getTree(headTreeId.get());
        Optional<NodeRef> points1Node = geogig.command(FindTreeChild.class)
                .setChildPath(NodeRef.appendChild(pointsName, idP1)).setParent(headTree).call();
        assertTrue(points1Node.isPresent());
        assertEquals(oId1, points1Node.get().getNode().getObjectId());

        assertEquals(c3.getId(), log.next().getId());
        assertEquals(c2.getId(), log.next().getId());
        assertEquals(c1.getId(), log.next().getId());
        assertFalse(log.hasNext());
    }

    @Test
    public void testRevertModifiedFeatureConflictAndAbort() throws Exception {
        insertAndAdd(points1);
        @SuppressWarnings("unused")
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        insertAndAdd(points1_modified);
        RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for modified " + idP1)
                .call();
        Feature points1_modifiedB = feature(pointsType, idP1, "StringProp1_2", new Integer(2000),
                "POINT(1 1)");
        ObjectId oId = insertAndAdd(points1_modifiedB);
        RevCommit c3 = geogig.command(CommitOp.class)
                .setMessage("commit for modified " + idP1 + " again").call();
        try {
            geogig.command(RevertOp.class).addCommit(c2.getId()).call();
            fail();
        } catch (RevertConflictsException e) {
            assertTrue(e.getMessage().contains(idP1));
        }

        Optional<Ref> ref = geogig.command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        assertTrue(ref.isPresent());
        assertEquals(c3.getId(), ref.get().getObjectId());

        List<Conflict> conflicts = Lists
                .newArrayList(geogig.command(ConflictsQueryOp.class).call());
        assertEquals(1, conflicts.size());
        String path = NodeRef.appendChild(pointsName, idP1);
        assertEquals(conflicts.get(0).getPath(), path);
        assertEquals(conflicts.get(0).getOurs(),
                RevFeature.builder().build(points1_modifiedB).getId());
        assertEquals(conflicts.get(0).getTheirs(), RevFeature.builder().build(points1).getId());

        geogig.command(RevertOp.class).setAbort(true).call();

        final Optional<Ref> currHead = geogig.command(RefParse.class).setName(Ref.HEAD).call();

        final Optional<ObjectId> headTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish(currHead.get().getObjectId()).call();
        RevTree headTree = repo.getTree(headTreeId.get());
        Optional<NodeRef> points1Node = geogig.command(FindTreeChild.class)
                .setChildPath(NodeRef.appendChild(pointsName, idP1)).setParent(headTree).call();
        assertTrue(points1Node.isPresent());
        assertEquals(oId, points1Node.get().getNode().getObjectId());

    }

    @Test
    public void testRevertEntireFeatureTypeTree() throws Exception {
        insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        insertAndAdd(points2);
        RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();
        insertAndAdd(points3);
        RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idP3).call();
        insertAndAdd(lines1);
        RevCommit c4 = geogig.command(CommitOp.class).setMessage("commit for " + idL1).call();

        geogig.command(RevertOp.class).addCommit(c4.getId()).call();

        final Optional<Ref> currHead = geogig.command(RefParse.class).setName(Ref.HEAD).call();

        final Optional<ObjectId> headTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish(currHead.get().getObjectId()).call();

        RevTree headTree = repo.getTree(headTreeId.get());

        Optional<NodeRef> lines1Node = geogig.command(FindTreeChild.class)
                .setChildPath(NodeRef.appendChild(linesName, idL1)).setParent(headTree).call();

        assertFalse(lines1Node.isPresent());

        @SuppressWarnings("unused")
        Optional<NodeRef> linesNode = geogig.command(FindTreeChild.class).setChildPath(linesName)
                .setParent(headTree).call();

        // assertFalse(linesNode.isPresent());

        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        log.next();
        assertEquals(c4.getId(), log.next().getId());
        assertEquals(c3.getId(), log.next().getId());
        assertEquals(c2.getId(), log.next().getId());
        assertEquals(c1.getId(), log.next().getId());
        assertFalse(log.hasNext());
    }
}
