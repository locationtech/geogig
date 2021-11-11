/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.model.NodeRef.appendChild;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Test;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.NothingToCommitException;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;

public class CommitOpTest extends RepositoryTestCase {

    private Geogig repo;

    protected @Override void setUpInternal() throws Exception {
        // These values should be used during a commit to set author/committer
        // TODO: author/committer roles need to be defined better, but for
        // now they are the same thing.
        super.repo.context().configDatabase().put("user.name", "groldan");
        super.repo.context().configDatabase().put("user.email", "groldan@test.com");
        this.repo = Geogig.of(super.repo.context());
    }

    @Test
    public void testNothingToCommit() throws Exception {
        repo.commands().add(".");
        assertThrows(NothingToCommitException.class, repo.commands().command(CommitOp.class)::call);
    }

    @Test
    public void testInitialCommit() throws Exception {
        ObjectId oid1 = insertAndAdd(points1);

        ObjectId oid2 = insertAndAdd(points2);

        repo.commands().add(".");
        RevCommit commit = repo.commands().command(CommitOp.class).call();
        assertNotNull(commit);
        assertNotNull(commit.getParentIds());
        assertEquals(0, commit.getParentIds().size());
        assertFalse(commit.parentN(0).isPresent());
        assertNotNull(commit.getId());
        assertEquals("groldan", commit.getAuthor().getName().get());
        assertEquals("groldan@test.com", commit.getAuthor().getEmail().get());

        ObjectId treeId = commit.getTreeId();

        assertNotNull(treeId);
        RevTree root = repo.objects().getTree(treeId);
        assertNotNull(root);

        Optional<Node> typeTreeId = repo.objects().tree(root).child(pointsName);
        assertTrue(typeTreeId.isPresent());

        RevTree typeTree = repo.objects().getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);

        String featureId = points1.getId();

        String path = NodeRef.appendChild(pointsName, featureId);
        Optional<Node> featureBlobId = repo.objects().tree(root).child(path);
        assertTrue(featureBlobId.isPresent());
        assertEquals(oid1, featureBlobId.get().getObjectId());

        featureId = points2.getId();
        featureBlobId = repo.objects().tree(root).child(NodeRef.appendChild(pointsName, featureId));
        assertTrue(featureBlobId.isPresent());
        assertEquals(oid2, featureBlobId.get().getObjectId());

        ObjectId commitId = repo.commands().command(RevParse.class).setRefSpec(Ref.HEAD).call()
                .get();
        assertEquals(commit.getId(), commitId);
    }

    @Test
    public void testCommitAddsFeatureTypeToObjectDatabase() throws Exception {
        insertAndAdd(points1);
        ObjectId id = RevFeatureType.builder().type(pointsType).build().getId();
        repo.commands().command(AddOp.class).addPattern(".").call();
        RevCommit commit = repo.commands().command(CommitOp.class).call();
        assertNotNull(commit);
        RevFeatureType type = repo.objects().getFeatureType(id);
        assertEquals(id, type.getId());
    }

    @Test
    public void testMultipleCommits() throws Exception {

        // insert and commit points1
        final ObjectId oId1_1 = insertAndAdd(points1);

        repo.commands().command(AddOp.class).call();
        final RevCommit commit1 = repo.commands().command(CommitOp.class).call();
        {
            assertCommit(commit1, null, null, null);
            // check points1 is there
            assertEquals(oId1_1,
                    repo.head().child(appendChild(pointsName, idP1)).get().getObjectId());
            // and check the objects were actually copied
            assertNotNull(repo.objects().get(oId1_1));
        }
        // insert and commit points2, points3 and lines1
        final ObjectId oId1_2 = insertAndAdd(points2);
        final ObjectId oId1_3 = insertAndAdd(points3);
        final ObjectId oId2_1 = insertAndAdd(lines1);

        repo.commands().command(AddOp.class).call();
        final RevCommit commit2 = repo.commands().command(CommitOp.class).setMessage("msg").call();
        {
            assertCommit(commit2, commit1.getId(), "groldan", "msg");

            // repo.getHeadTree().accept(
            // new PrintVisitor(repo.objectDatabase(), new PrintWriter(System.out)));

            // check points2, points3 and lines1
            assertEquals(oId1_2,
                    repo.head().child(appendChild(pointsName, idP2)).get().getObjectId());
            assertEquals(oId1_3,
                    repo.head().child(appendChild(pointsName, idP3)).get().getObjectId());
            assertEquals(oId2_1,
                    repo.head().child(appendChild(linesName, idL1)).get().getObjectId());
            // and check the objects were actually copied
            assertNotNull(repo.objects().get(oId1_2));
            assertNotNull(repo.objects().get(oId1_3));
            assertNotNull(repo.objects().get(oId2_1));

            // as well as feature1_1 from the previous commit
            assertEquals(oId1_1,
                    repo.head().child(appendChild(pointsName, idP1)).get().getObjectId());
        }
        // delete feature1_1, feature1_3, and feature2_1
        assertTrue(deleteAndAdd(points1));
        assertTrue(deleteAndAdd(points3));
        assertTrue(deleteAndAdd(lines1));
        // and insert feature2_2
        final ObjectId oId2_2 = insertAndAdd(lines2);

        repo.commands().command(AddOp.class).call();
        final RevCommit commit3 = repo.commands().command(CommitOp.class).call();
        {
            assertCommit(commit3, commit2.getId(), "groldan", null);

            // repo.getHeadTree().accept(
            // new PrintVisitor(repo.objectDatabase(), new PrintWriter(System.out)));

            // check only points2 and lines2 remain
            assertFalse(repo.head().child(appendChild(pointsName, idP1)).isPresent());
            assertFalse(repo.head().child(appendChild(pointsName, idP3)).isPresent());
            assertFalse(repo.head().child(appendChild(linesName, idL3)).isPresent());

            assertEquals(oId1_2,
                    repo.head().child(appendChild(pointsName, idP2)).get().getObjectId());
            assertEquals(oId2_2,
                    repo.head().child(appendChild(linesName, idL2)).get().getObjectId());
            // and check the objects were actually copied
            assertNotNull(repo.objects().get(oId1_2));
            assertNotNull(repo.objects().get(oId2_2));
        }
    }

    @Test
    public void testCommitWithCustomAuthorAndCommitter() throws Exception {

        ObjectId oid1 = insertAndAdd(points1);

        ObjectId oid2 = insertAndAdd(points2);

        repo.commands().command(AddOp.class).addPattern(".").call();
        CommitOp commitCommand = repo.commands().command(CommitOp.class);
        commitCommand.setAuthor("John Doe", "John@Doe.com");
        commitCommand.setCommitter("Jane Doe", "Jane@Doe.com");
        RevCommit commit = commitCommand.call();
        assertNotNull(commit);
        assertNotNull(commit.getParentIds());
        assertEquals(0, commit.getParentIds().size());
        assertFalse(commit.parentN(0).isPresent());
        assertNotNull(commit.getId());
        assertEquals("John Doe", commit.getAuthor().getName().get());
        assertEquals("John@Doe.com", commit.getAuthor().getEmail().get());
        assertEquals("Jane Doe", commit.getCommitter().getName().get());
        assertEquals("Jane@Doe.com", commit.getCommitter().getEmail().get());

        ObjectId treeId = commit.getTreeId();

        assertNotNull(treeId);
        RevTree root = repo.objects().getTree(treeId);
        assertNotNull(root);

        Optional<Node> typeTreeId = repo.objects().tree(root).child(pointsName);
        assertTrue(typeTreeId.isPresent());

        RevTree typeTree = repo.objects().getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);

        String featureId = points1.getId();
        Optional<Node> featureBlobId = repo.objects().tree(root)
                .child(NodeRef.appendChild(pointsName, featureId));
        assertTrue(featureBlobId.isPresent());
        assertEquals(oid1, featureBlobId.get().getObjectId());

        featureId = points2.getId();
        featureBlobId = repo.objects().tree(root).child(NodeRef.appendChild(pointsName, featureId));
        assertTrue(featureBlobId.isPresent());
        assertEquals(oid2, featureBlobId.get().getObjectId());

        ObjectId commitId = repo.commands().command(RevParse.class).setRefSpec(Ref.HEAD).call()
                .get();
        assertEquals(commit.getId(), commitId);
    }

    @Test
    public void testCommitWithExplicitNullAuthorEmail() throws Exception {

        CommitOp commitCommand = repo.commands().command(CommitOp.class);
        commitCommand.setAuthor("John Doe", null);
        commitCommand.setAllowEmpty(true);
        RevCommit commit = commitCommand.call();
        assertFalse(commit.getAuthor().getEmail().isPresent());

        assertEquals("groldan", commit.getCommitter().getName().get());
        assertEquals("groldan@test.com", commit.getCommitter().getEmail().get());
        assertEquals("John Doe", commit.getAuthor().getName().get());
    }

    @Test
    public void testExplicitTimeStamp() throws Exception {

        CommitOp commitCommand = repo.commands().command(CommitOp.class);
        commitCommand.setAuthorTimestamp(1000L);
        commitCommand.setAuthorTimeZoneOffset(-3);
        commitCommand.setCommitterTimestamp(2000L);
        commitCommand.setCommitterTimeZoneOffset(+4);

        commitCommand.setAllowEmpty(true);
        RevCommit commit = commitCommand.call();
        assertEquals(1000L, commit.getAuthor().getTimestamp());
        assertEquals(-3, commit.getAuthor().getTimeZoneOffset());
        assertEquals(2000L, commit.getCommitter().getTimestamp());
        assertEquals(+4, commit.getCommitter().getTimeZoneOffset());
    }

    @Test
    public void testCommitWithAllOption() throws Exception {

        insertAndAdd(points1);

        repo.commands().command(AddOp.class).addPattern(".").call();
        RevCommit commit = repo.commands().command(CommitOp.class).call();

        ObjectId oid = insertAndAdd(points1_modified);

        CommitOp commitCommand = repo.commands().command(CommitOp.class);
        commit = commitCommand.setAll(true).call();
        assertNotNull(commit);
        assertNotNull(commit.getParentIds());
        assertEquals(1, commit.getParentIds().size());
        assertNotNull(commit.getId());

        ObjectId treeId = commit.getTreeId();

        assertNotNull(treeId);
        RevTree root = repo.objects().getTree(treeId);
        assertNotNull(root);

        Optional<Node> typeTreeId = repo.objects().tree(root).child(pointsName);
        assertTrue(typeTreeId.isPresent());

        RevTree typeTree = repo.objects().getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);

        String featureId = points1.getId();
        Optional<Node> featureBlobId = repo.objects().tree(root)
                .child(NodeRef.appendChild(pointsName, featureId));
        assertTrue(featureBlobId.isPresent());
        assertEquals(oid, featureBlobId.get().getObjectId());

        ObjectId commitId = repo.commands().command(RevParse.class).setRefSpec(Ref.HEAD).call()
                .get();
        assertEquals(commit.getId(), commitId);
    }

    @Test
    public void testCommitWithAllOptionAndPaths() throws Exception {

        insertAndAdd(points1);

        repo.commands().command(AddOp.class).addPattern(".").call();
        RevCommit commit = repo.commands().command(CommitOp.class).call();

        ObjectId oid = insertAndAdd(points1_modified);
        insert(points2);
        insert(lines1);

        CommitOp commitCommand = repo.commands().command(CommitOp.class);
        commit = commitCommand.setPathFilters(Arrays.asList(pointsName)).setAll(true).call();
        assertNotNull(commit);
        assertNotNull(commit.getParentIds());
        assertEquals(1, commit.getParentIds().size());
        assertNotNull(commit.getId());

        ObjectId treeId = commit.getTreeId();

        assertNotNull(treeId);
        RevTree root = repo.objects().getTree(treeId);
        assertNotNull(root);

        Optional<Node> linesTreeId = repo.objects().tree(root).child(linesName);
        assertFalse(linesTreeId.isPresent());

        Optional<Node> typeTreeId = repo.objects().tree(root).child(pointsName);
        assertTrue(typeTreeId.isPresent());

        RevTree typeTree = repo.objects().getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);

        String featureId = points1.getId();
        Optional<Node> featureBlobId = repo.objects().tree(root)
                .child(NodeRef.appendChild(pointsName, featureId));
        assertTrue(featureBlobId.isPresent());
        assertEquals(oid, featureBlobId.get().getObjectId());

        featureId = points2.getId();
        featureBlobId = repo.objects().tree(root).child(NodeRef.appendChild(pointsName, featureId));
        assertFalse(featureBlobId.isPresent());

        ObjectId commitId = repo.commands().command(RevParse.class).setRefSpec(Ref.HEAD).call()
                .get();
        assertEquals(commit.getId(), commitId);
    }

    @Test
    public void testEmptyCommit() throws Exception {

        CommitOp commitCommand = repo.commands().command(CommitOp.class);
        RevCommit commit = commitCommand.setAllowEmpty(true).call();
        assertNotNull(commit);
        assertNotNull(commit.getParentIds());
        assertEquals(0, commit.getParentIds().size());
        assertFalse(commit.parentN(0).isPresent());
        assertNotNull(commit.getId());

        ObjectId commitId = repo.commands().command(RevParse.class).setRefSpec(Ref.HEAD).call()
                .get();
        assertEquals(commit.getId(), commitId);
    }

    @Test
    public void testNoCommitterName() throws Exception {

        repo.config().remove("user.name");

        CommitOp commitCommand = repo.commands().command(CommitOp.class).setAllowEmpty(true);
        assertThrows(IllegalStateException.class, commitCommand::call);
    }

    @Test
    public void testNoCommitterEmail() throws Exception {
        repo.config().remove("user.email");
        CommitOp commitCommand = repo.commands().command(CommitOp.class).setAllowEmpty(true);
        assertThrows(IllegalStateException.class, commitCommand::call);
    }

    @Test
    public void testCancel() throws Exception {
        ProgressListener listener1 = mock(ProgressListener.class);
        when(listener1.isCanceled()).thenReturn(true);

        ProgressListener listener2 = mock(ProgressListener.class);
        when(listener2.isCanceled()).thenReturn(false, true);

        ProgressListener listener3 = mock(ProgressListener.class);
        when(listener3.isCanceled()).thenReturn(false, false, true);

        CommitOp commitCommand1 = repo.commands().command(CommitOp.class);
        commitCommand1.setProgressListener(listener1);
        assertNull(commitCommand1.setAllowEmpty(true).call());

        CommitOp commitCommand2 = repo.commands().command(CommitOp.class);
        commitCommand2.setProgressListener(listener2);
        assertNull(commitCommand2.setAllowEmpty(true).call());

        CommitOp commitCommand3 = repo.commands().command(CommitOp.class);
        commitCommand3.setProgressListener(listener3);
        assertNull(commitCommand3.setAllowEmpty(true).call());

        verify(listener1, times(1)).isCanceled();
        verify(listener2, times(2)).isCanceled();
        verify(listener3, times(3)).isCanceled();
    }

    @Test
    public void testCommitEmptyTreeOnEmptyRepo() throws Exception {
        WorkingTree workingTree = repo.getContext().workingTree();
        final String emptyTreeName = "emptyTree";

        workingTree.createTypeTree(emptyTreeName, pointsType);
        repo.commands().command(AddOp.class).addPattern(emptyTreeName).call();

        CommitOp commitCommand = repo.commands().command(CommitOp.class);
        RevCommit commit = commitCommand.call();
        assertNotNull(commit);

        RevTree head = repo.commands().command(RevObjectParse.class).setObjectId(commit.getTreeId())
                .call(RevTree.class).get();
        Optional<NodeRef> ref = repo.commands().command(FindTreeChild.class)
                .setChildPath(emptyTreeName).setParent(head).call();
        assertTrue(ref.isPresent());
    }

    @Test
    public void testCommitEmptyTreeOnNonEmptyRepo() throws Exception {
        insertAndAdd(points1, points2);
        repo.commands().command(CommitOp.class).call();

        // insertAndAdd(lines1, lines2);

        WorkingTree workingTree = repo.getContext().workingTree();
        final String emptyTreeName = "emptyTree";

        workingTree.createTypeTree(emptyTreeName, pointsType);
        {
            List<DiffEntry> unstaged = toList(workingTree.getUnstaged(null));
            assertEquals(unstaged.toString(), 1, unstaged.size());
            // assertEquals(NodeRef.ROOT, unstaged.get(0).newName());
            assertEquals(emptyTreeName, unstaged.get(0).name());
        }
        repo.commands().command(AddOp.class).call();
        {
            StagingArea index = repo.getContext().stagingArea();
            List<DiffEntry> staged = toList(index.getStaged(null));
            assertEquals(staged.toString(), 1, staged.size());
            assertEquals(emptyTreeName, staged.get(0).name());
        }
        CommitOp commitCommand = repo.commands().command(CommitOp.class);
        RevCommit commit = commitCommand.call();
        assertNotNull(commit);

        RevTree head = repo.commands().command(RevObjectParse.class).setObjectId(commit.getTreeId())
                .call(RevTree.class).get();
        Optional<NodeRef> ref = repo.commands().command(FindTreeChild.class)
                .setChildPath(emptyTreeName).setParent(head).call();
        assertTrue(ref.isPresent());
    }

    @Test
    public void testCommitUsingCommit() throws Exception {
        insertAndAdd(points1);
        final RevCommit commit = repo.commands().command(CommitOp.class)
                .setCommitter("anothercommitter", "anothercommitter@test.com").call();
        insertAndAdd(points2);
        RevCommit commit2 = repo.commands().command(CommitOp.class).setCommit(commit).call();
        assertEquals(commit.getMessage(), commit2.getMessage());
        assertEquals(commit.getAuthor(), commit2.getAuthor());
        assertNotSame(commit.getCommitter(), commit2.getCommitter());
    }

    @Test
    public void testCommitUsingCommitAndMessage() throws Exception {
        String message = "A message";
        insertAndAdd(points1);
        final RevCommit commit = repo.commands().command(CommitOp.class)
                .setCommitter("anothercommitter", "anothercommitter@test.com").call();
        insertAndAdd(points2);
        RevCommit commit2 = repo.commands().command(CommitOp.class).setCommit(commit)
                .setMessage(message).call();
        assertNotSame(commit.getMessage(), commit2.getMessage());
        assertEquals(commit.getAuthor(), commit2.getAuthor());
        assertNotSame(commit.getCommitter(), commit2.getCommitter());
        assertEquals(message, commit2.getMessage());
    }

    @Test
    public void testCommitWithDeletedTree() throws Exception {
        insertAndAdd(points1, points2);
        insertAndAdd(lines1, lines2);
        final RevCommit commit1 = repo.commands().command(CommitOp.class).call();

        final RevTree tree1 = repo.commands().command(RevObjectParse.class)
                .setObjectId(commit1.getTreeId()).call(RevTree.class).get();
        assertEquals(2, tree1.trees().size());

        WorkingTree workingTree = repo.getContext().workingTree();
        workingTree.delete(pointsName);
        repo.commands().command(AddOp.class).call();

        final RevCommit commit2 = repo.commands().command(CommitOp.class).call();

        RevTree tree2 = repo.commands().command(RevObjectParse.class)
                .setObjectId(commit2.getTreeId()).call(RevTree.class).get();

        assertEquals(1, tree2.trees().size());
    }

    @Test
    public void testAmend() throws Exception {

        final ObjectId id = insertAndAdd(points1);
        final RevCommit commit1 = repo.commands().command(CommitOp.class).setMessage("Message")
                .call();
        {
            assertCommit(commit1, null, null, null);
            assertEquals(id, repo.head().child(appendChild(pointsName, idP1)).get().getObjectId());
            assertNotNull(repo.objects().get(id));
        }

        final ObjectId id2 = insertAndAdd(points2);
        final RevCommit commit2 = repo.commands().command(CommitOp.class).setAmend(true).call();
        {
            assertCommit(commit2, null, "groldan", "Message");
            Optional<RevFeature> p2 = repo.commands().command(RevObjectParse.class)
                    .setRefSpec("HEAD:" + appendChild(pointsName, idP2)).call(RevFeature.class);
            assertTrue(p2.isPresent());
            assertEquals(id2, p2.get().getId());
            Optional<RevFeature> p1 = repo.commands().command(RevObjectParse.class)
                    .setRefSpec("HEAD:" + appendChild(pointsName, idP1)).call(RevFeature.class);
            assertTrue(p1.isPresent());
            assertEquals(id, p1.get().getId());
        }
        Iterator<RevCommit> log = repo.commands().command(LogOp.class).call();
        assertTrue(log.hasNext());
        log.next();
        assertFalse(log.hasNext());

    }

    @Test
    public void testAmendCommitMessage() throws Exception {

        final ObjectId id = insertAndAdd(points1);
        final RevCommit commit1 = repo.commands().command(CommitOp.class).setMessage("Message")
                .call();
        {
            assertCommit(commit1, null, null, null);
            assertEquals(id, repo.head().child(appendChild(pointsName, idP1)).get().getObjectId());
            assertNotNull(repo.objects().get(id));
        }

        final RevCommit commit2 = repo.commands().command(CommitOp.class).setAmend(true)
                .setMessage("Updated Message").call();
        {
            assertCommit(commit2, null, "groldan", "Updated Message");
            Optional<RevFeature> p1 = repo.commands().command(RevObjectParse.class)
                    .setRefSpec("HEAD:" + appendChild(pointsName, idP1)).call(RevFeature.class);
            assertTrue(p1.isPresent());
            assertEquals(id, p1.get().getId());
            assertEquals(commit1.getAuthor().getName(), commit2.getAuthor().getName());
            assertEquals(commit1.getAuthor().getEmail(), commit2.getAuthor().getEmail());
            assertEquals(commit1.getCommitter().getName(), commit2.getCommitter().getName());
            assertEquals(commit1.getCommitter().getEmail(), commit2.getCommitter().getEmail());
            assertEquals(commit1.getParentIds(), commit2.getParentIds());
            assertEquals(commit1.getTreeId(), commit2.getTreeId());
        }
        Iterator<RevCommit> log = repo.commands().command(LogOp.class).call();
        assertTrue(log.hasNext());
        log.next();
        assertFalse(log.hasNext());

    }

    @Test
    public void testAmendTimestamp() throws Exception {

        final ObjectId id = insertAndAdd(points1);
        final RevCommit commit1 = repo.commands().command(CommitOp.class).setMessage("Message")
                .call();
        {
            assertCommit(commit1, null, null, null);
            assertEquals(id, repo.head().child(appendChild(pointsName, idP1)).get().getObjectId());
            assertNotNull(repo.objects().get(id));
        }

        final Long newTimestamp = 5L;

        final RevCommit commit2 = repo.commands().command(CommitOp.class).setAmend(true)
                .setCommitterTimestamp(newTimestamp).call();
        {
            assertCommit(commit2, null, "groldan", "Message");
            Optional<RevFeature> p1 = repo.commands().command(RevObjectParse.class)
                    .setRefSpec("HEAD:" + appendChild(pointsName, idP1)).call(RevFeature.class);
            assertTrue(p1.isPresent());
            assertEquals(id, p1.get().getId());
            assertEquals(commit1.getAuthor().getName(), commit2.getAuthor().getName());
            assertEquals(commit1.getAuthor().getEmail(), commit2.getAuthor().getEmail());
            assertEquals(commit1.getCommitter().getName(), commit2.getCommitter().getName());
            assertEquals(commit1.getCommitter().getEmail(), commit2.getCommitter().getEmail());
            assertEquals(newTimestamp.longValue(), commit2.getCommitter().getTimestamp());
            assertEquals(commit1.getMessage(), commit2.getMessage());
            assertEquals(commit1.getParentIds(), commit2.getParentIds());
            assertEquals(commit1.getTreeId(), commit2.getTreeId());
        }
        Iterator<RevCommit> log = repo.commands().command(LogOp.class).call();
        assertTrue(log.hasNext());
        log.next();
        assertFalse(log.hasNext());

    }

    @Test
    public void testAmendReUseCommit() throws Exception {

        final ObjectId id = insertAndAdd(points1);
        final RevCommit commit1 = repo.commands().command(CommitOp.class).setMessage("Message")
                .call();
        {
            assertCommit(commit1, null, null, null);
            assertEquals(id, repo.head().child(appendChild(pointsName, idP1)).get().getObjectId());
            assertNotNull(repo.objects().get(id));
        }

        final RevCommit commit2 = repo.commands().command(CommitOp.class).setAmend(true)
                .setCommit(commit1).call();
        {
            assertCommit(commit2, null, "groldan", "Message");
            Optional<RevFeature> p1 = repo.commands().command(RevObjectParse.class)
                    .setRefSpec("HEAD:" + appendChild(pointsName, idP1)).call(RevFeature.class);
            assertTrue(p1.isPresent());
            assertEquals(id, p1.get().getId());
            assertEquals(commit1.getAuthor(), commit2.getAuthor());
            assertEquals(commit1.getCommitter().getName(), commit2.getCommitter().getName());
            assertEquals(commit1.getCommitter().getEmail(), commit2.getCommitter().getEmail());
            assertEquals(commit1.getMessage(), commit2.getMessage());
            assertEquals(commit1.getParentIds(), commit2.getParentIds());
            assertEquals(commit1.getTreeId(), commit2.getTreeId());
        }
        Iterator<RevCommit> log = repo.commands().command(LogOp.class).call();
        assertTrue(log.hasNext());
        log.next();
        assertFalse(log.hasNext());
    }

    @Test
    public void testAmendNoChanges() throws Exception {

        final ObjectId id = insertAndAdd(points1);
        final RevCommit commit1 = repo.commands().command(CommitOp.class).setMessage("Message")
                .call();
        {
            assertCommit(commit1, null, null, null);
            assertEquals(id, repo.head().child(appendChild(pointsName, idP1)).get().getObjectId());
            assertNotNull(repo.objects().get(id));
        }

        assertThat(
                assertThrows(IllegalArgumentException.class,
                        repo.commands().command(CommitOp.class).setAmend(true)::call).getMessage(),
                containsString(
                        "You must specify a new commit message, timestamp, or commit to reuse when amending a commit with no changes."));
    }

    @Test
    public void testCannotAmend() throws Exception {

        insertAndAdd(points1);
        try {
            repo.commands().command(CommitOp.class).setAmend(true).call();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);

        }

    }

    private void assertCommit(RevCommit commit, @Nullable ObjectId parentId, String author,
            String message) {

        assertNotNull(commit);
        assertEquals(parentId == null ? 0 : 1, commit.getParentIds().size());
        assertEquals(parentId, commit.parentN(0).orElse(null));
        assertNotNull(commit.getTreeId());
        assertNotNull(commit.getId());
        if (author != null) {
            assertEquals(author, commit.getAuthor().getName().get());
        }
        if (message != null) {
            assertEquals(message, commit.getMessage());
        }
        assertNotNull(repo.objects().getTree(commit.getTreeId()));
        Ref head = getRepository().command(RefParse.class).setName(Ref.HEAD).call().get();
        assertEquals(commit.getId(), head.getObjectId());
    }

    @Test
    public void testPathFiltering() throws Exception {
        insertAndAdd(points1);
        insertAndAdd(points2);

        RevCommit commit = repo.commands().command(CommitOp.class).call();

        insertAndAdd(points3);

        insertAndAdd(lines1);
        insertAndAdd(lines2);
        insertAndAdd(lines3);

        List<String> filters = Arrays.asList("Points/Points.3", "Lines/Lines.1", "Lines/Lines.3");
        commit = repo.commands().command(CommitOp.class).setPathFilters(filters).call();

        assertNotNull(commit);
        assertNotNull(commit.getParentIds());
        assertEquals(1, commit.getParentIds().size());
        assertNotNull(commit.getId());

        ObjectId treeId = commit.getTreeId();

        assertNotNull(treeId);
        RevTree root = repo.objects().getTree(treeId);
        assertNotNull(root);

        Optional<Node> typeTreeId = repo.objects().tree(root).child(pointsName);
        assertTrue(typeTreeId.isPresent());
        RevTree typeTree = repo.objects().getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);

        String featureId = points1.getId();
        Optional<Node> featureBlobId = repo.objects().tree(root)
                .child(NodeRef.appendChild(pointsName, featureId));
        assertTrue(featureBlobId.isPresent());

        featureId = points2.getId();
        featureBlobId = repo.objects().tree(root).child(NodeRef.appendChild(pointsName, featureId));
        assertTrue(featureBlobId.isPresent());

        featureId = points3.getId();
        featureBlobId = repo.objects().tree(root).child(NodeRef.appendChild(pointsName, featureId));
        assertTrue(featureBlobId.isPresent());

        typeTreeId = repo.objects().tree(root).child(linesName);
        assertTrue(typeTreeId.isPresent());
        typeTree = repo.objects().getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);

        featureId = lines1.getId();
        featureBlobId = repo.objects().tree(root).child(NodeRef.appendChild(linesName, featureId));
        assertTrue(featureBlobId.isPresent());

        featureId = lines2.getId();
        featureBlobId = repo.objects().tree(root).child(NodeRef.appendChild(linesName, featureId));
        assertFalse(featureBlobId.isPresent());

        featureId = lines3.getId();
        featureBlobId = repo.objects().tree(root).child(NodeRef.appendChild(linesName, featureId));
        assertTrue(featureBlobId.isPresent());
    }

    @Test
    public void testPathFilteringWithUnstaged() throws Exception {
        insertAndAdd(points1);
        insertAndAdd(points2);

        RevCommit commit = repo.commands().command(CommitOp.class).call();

        insertAndAdd(lines1);
        insertAndAdd(lines3);
        insert(lines2);
        insert(points3);

        List<String> filters = Arrays.asList(pointsName, linesName);
        commit = repo.commands().command(CommitOp.class).setPathFilters(filters).call();

        assertNotNull(commit);
        assertNotNull(commit.getParentIds());
        assertEquals(1, commit.getParentIds().size());
        assertNotNull(commit.getId());

        ObjectId treeId = commit.getTreeId();

        assertNotNull(treeId);
        RevTree root = repo.objects().getTree(treeId);
        assertNotNull(root);

        Optional<Node> typeTreeId = repo.objects().tree(root).child(pointsName);
        assertTrue(typeTreeId.isPresent());
        RevTree typeTree = repo.objects().getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);

        String featureId = points1.getId();
        Optional<Node> featureBlobId = repo.objects().tree(root)
                .child(NodeRef.appendChild(pointsName, featureId));
        assertTrue(featureBlobId.isPresent());

        featureId = points2.getId();
        featureBlobId = repo.objects().tree(root).child(NodeRef.appendChild(pointsName, featureId));
        assertTrue(featureBlobId.isPresent());

        featureId = points3.getId();
        featureBlobId = repo.objects().tree(root).child(NodeRef.appendChild(pointsName, featureId));
        assertFalse(featureBlobId.isPresent());

        typeTreeId = repo.objects().tree(root).child(linesName);
        assertTrue(typeTreeId.isPresent());
        typeTree = repo.objects().getTree(typeTreeId.get().getObjectId());
        assertNotNull(typeTree);

        featureId = lines1.getId();
        featureBlobId = repo.objects().tree(root).child(NodeRef.appendChild(linesName, featureId));
        assertTrue(featureBlobId.isPresent());

        featureId = lines2.getId();
        featureBlobId = repo.objects().tree(root).child(NodeRef.appendChild(linesName, featureId));
        assertFalse(featureBlobId.isPresent());

        featureId = lines3.getId();
        featureBlobId = repo.objects().tree(root).child(NodeRef.appendChild(linesName, featureId));
        assertTrue(featureBlobId.isPresent());
    }
}
