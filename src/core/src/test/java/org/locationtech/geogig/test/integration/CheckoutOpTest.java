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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutException;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CheckoutResult;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.RemoveOp;
import org.opengis.feature.Feature;

import com.google.common.base.Optional;

public class CheckoutOpTest extends RepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    private Feature points1ModifiedB;

    private Feature points1Modified;

    @Override
    protected void setUpInternal() throws Exception {
        // These values should be used during a commit to set author/committer
        // TODO: author/committer roles need to be defined better, but for
        // now they are the same thing.
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.name")
                .setValue("groldan").call();
        repo.command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setName("user.email")
                .setValue("groldan@boundlessgeo.com").call();
        points1ModifiedB = feature(pointsType, idP1, "StringProp1_3", new Integer(2000),
                "POINT(1 1)");
        points1Modified = feature(pointsType, idP1, "StringProp1_2", new Integer(1000),
                "POINT(1 1)");
    }

    @Test
    public void testAddPaths() throws Exception {
        ObjectId oID1 = insertAndAdd(points1);
        insertAndAdd(points2);
        insertAndAdd(points3);
        ObjectId oID2 = insertAndAdd(lines1);

        geogig.command(CommitOp.class).setMessage("commit for all features").call();

        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("testBranch").call();

        ObjectId oID1Modified = insertAndAdd(points1_modified);
        ObjectId oID3 = insertAndAdd(lines2);
        ObjectId oID4 = insertAndAdd(lines3);
        geogig.command(CommitOp.class).setMessage("commit for modified points1").call();

        List<String> paths = Arrays.asList(
                NodeRef.appendChild(pointsName, points1.getIdentifier().getID()),
                NodeRef.appendChild(linesName, lines1.getIdentifier().getID()));

        RevTree root = repo.workingTree().getTree();

        Optional<Node> featureBlob1 = repo.getTreeChild(root, paths.get(0));
        assertEquals(oID1Modified, featureBlob1.get().getObjectId());

        Optional<Node> featureBlob2 = repo.getTreeChild(root, paths.get(1));
        assertEquals(oID2, featureBlob2.get().getObjectId());

        Optional<Node> featureBlob3 = repo.getTreeChild(root,
                NodeRef.appendChild(linesName, lines2.getIdentifier().getID()));
        assertEquals(oID3, featureBlob3.get().getObjectId());

        Optional<Node> featureBlob4 = repo.getTreeChild(root,
                NodeRef.appendChild(linesName, lines3.getIdentifier().getID()));
        assertEquals(oID4, featureBlob4.get().getObjectId());

        geogig.command(CheckoutOp.class).setSource("master").addPaths(paths).call();

        root = repo.workingTree().getTree();

        featureBlob1 = repo.getTreeChild(root, paths.get(0));
        assertEquals(oID1, featureBlob1.get().getObjectId());

        featureBlob2 = repo.getTreeChild(root, paths.get(1));
        assertEquals(oID2, featureBlob2.get().getObjectId());

        featureBlob3 = repo.getTreeChild(root,
                NodeRef.appendChild(linesName, lines2.getIdentifier().getID()));
        assertEquals(oID3, featureBlob3.get().getObjectId());

        featureBlob4 = repo.getTreeChild(root,
                NodeRef.appendChild(linesName, lines3.getIdentifier().getID()));
        assertEquals(oID4, featureBlob4.get().getObjectId());

    }

    @Test
    public void testCheckoutCommitDettachedHead() throws Exception {
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();

        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();

        insertAndAdd(lines1);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idL2).call();

        CheckoutResult result;
        result = geogig.command(CheckoutOp.class).setSource(c1.getId().toString()).call();
        assertEquals(c1.getTreeId(), result.getNewTree());

        assertFalse(
                geogig.command(RefParse.class).setName(Ref.HEAD).call().get() instanceof SymRef);
        assertTrue(geogig.command(RefParse.class).setName(Ref.HEAD).call().get() instanceof Ref);

        result = geogig.command(CheckoutOp.class).setSource(c2.getId().toString()).call();
        assertEquals(c2.getTreeId(), result.getNewTree());

        result = geogig.command(CheckoutOp.class).setSource(c3.getId().toString()).call();
        assertEquals(c3.getTreeId(), result.getNewTree());
    }

    @Test
    public void testCheckoutBranch() throws Exception {
        insertAndAdd(points1);
        final RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        final Ref branch1 = geogig.command(BranchCreateOp.class).setName("branch1").call();

        insertAndAdd(points2);
        final RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit for " + idP2).call();
        final Ref branch2 = geogig.command(BranchCreateOp.class).setName("branch2").call();

        insertAndAdd(lines1);
        final RevCommit c3 = geogig.command(CommitOp.class).setMessage("commit for " + idL2).call();
        final Ref branch3 = geogig.command(BranchCreateOp.class).setName("branch3").call();

        CheckoutResult result;
        result = geogig.command(CheckoutOp.class).setSource("branch1").call();
        assertEquals(c1.getTreeId(), result.getNewTree());
        assertTrue(geogig.command(RefParse.class).setName(Ref.HEAD).call().get() instanceof SymRef);
        assertEquals(branch1.getName(),
                ((SymRef) geogig.command(RefParse.class).setName(Ref.HEAD).call().get())
                        .getTarget());

        result = geogig.command(CheckoutOp.class).setSource("branch2").call();
        assertEquals(c2.getTreeId(), result.getNewTree());
        assertTrue(geogig.command(RefParse.class).setName(Ref.HEAD).call().get() instanceof SymRef);
        assertEquals(branch2.getName(),
                ((SymRef) geogig.command(RefParse.class).setName(Ref.HEAD).call().get())
                        .getTarget());

        result = geogig.command(CheckoutOp.class).setSource("branch3").call();
        assertEquals(c3.getTreeId(), result.getNewTree());
        assertTrue(geogig.command(RefParse.class).setName(Ref.HEAD).call().get() instanceof SymRef);
        assertEquals(branch3.getName(),
                ((SymRef) geogig.command(RefParse.class).setName(Ref.HEAD).call().get())
                        .getTarget());
    }

    @Test
    public void testCheckoutPathFilter() throws Exception {
        ObjectId points1Id = insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        insert(points1_modified);

        CheckoutResult result = geogig.command(CheckoutOp.class).addPath("Points/Points.1").call();

        Optional<RevTree> workTree = geogig.command(RevObjectParse.class)
                .setObjectId(result.getNewTree()).call(RevTree.class);

        Optional<NodeRef> nodeRef = geogig.command(FindTreeChild.class).setParent(workTree.get())
                .setChildPath("Points/Points.1").call();

        assertEquals(points1Id, nodeRef.get().getNode().getObjectId());
    }

    @Test
    public void testCheckoutPathFilterWithNothingInIndex() throws Exception {
        insertAndAdd(points2);
        insert(points1_modified);

        exception.expect(IllegalArgumentException.class);
        geogig.command(CheckoutOp.class).addPath("Points/Points.1").call();

    }

    @Test
    public void testCheckoutPathFilterWithMultiplePaths() throws Exception {
        ObjectId points1Id = insertAndAdd(points1);
        ObjectId lines1Id = insertAndAdd(lines1);
        geogig.command(CommitOp.class).setMessage("commit 1").call();
        insert(points1_modified);
        insert(lines2);
        Collection<String> paths = Arrays.asList("Points/Points.1", "Lines");
        CheckoutResult result = geogig.command(CheckoutOp.class).addPaths(paths).call();
        Optional<RevTree> workTree = geogig.command(RevObjectParse.class)
                .setObjectId(result.getNewTree()).call(RevTree.class);
        Optional<NodeRef> nodeRef = geogig.command(FindTreeChild.class).setParent(workTree.get())
                .setChildPath("Points/Points.1").call();

        assertEquals(points1Id, nodeRef.get().getNode().getObjectId());

        nodeRef = geogig.command(FindTreeChild.class).setParent(workTree.get())
                .setChildPath("Lines/Lines.1").call();

        assertEquals(lines1Id, nodeRef.get().getNode().getObjectId());
        nodeRef = geogig.command(FindTreeChild.class).setParent(workTree.get())
                .setChildPath("Lines/Lines.2").call();
        assertFalse(nodeRef.isPresent());
    }

    @Test
    public void testCheckoutPathFilterWithTreeOtherThanIndex() throws Exception {
        ObjectId points1Id = insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit 1").call();
        ObjectId points2Id = insertAndAdd(points2);
        RevCommit c2 = geogig.command(CommitOp.class).setMessage("commit 2").call();
        insertAndAdd(points3);
        geogig.command(CommitOp.class).setMessage("commit 3").call();
        insert(points1_modified);

        CheckoutResult result = geogig.command(CheckoutOp.class)
                .setSource(c2.getTreeId().toString()).addPath("Points").call();

        Optional<RevTree> workTree = geogig.command(RevObjectParse.class)
                .setObjectId(result.getNewTree()).call(RevTree.class);
        Optional<NodeRef> nodeRef = geogig.command(FindTreeChild.class).setParent(workTree.get())
                .setChildPath("Points/Points.1").call();

        assertEquals(points1Id, nodeRef.get().getNode().getObjectId());

        nodeRef = geogig.command(FindTreeChild.class).setParent(workTree.get())
                .setChildPath("Points/Points.2").call();

        assertEquals(points2Id, nodeRef.get().getNode().getObjectId());

        nodeRef = geogig.command(FindTreeChild.class).setParent(workTree.get())
                .setChildPath("Points/Points.3").call();

        assertFalse(nodeRef.isPresent());
    }

    @Test
    public void testCheckoutNoParametersSet() {
        exception.expect(IllegalStateException.class);
        geogig.command(CheckoutOp.class).call();
    }

    @Test
    public void testCheckoutBranchWithChangesInTheIndex() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();
        insertAndAdd(points2);
        exception.expect(CheckoutException.class);
        geogig.command(CheckoutOp.class).setSource("branch1").call();
    }

    @Test
    public void testCheckoutBranchWithChangesInTheWorkTree() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        geogig.command(BranchCreateOp.class).setName("branch1").call();
        insert(points2);
        exception.expect(CheckoutException.class);
        geogig.command(CheckoutOp.class).setSource("branch1").call();
    }

    @Test
    public void testCheckoutBranchWithForceOptionAndChangesInTheIndex() throws Exception {
        insertAndAdd(points1);
        RevCommit c1 = geogig.command(CommitOp.class).setMessage("commit for " + idP1).call();
        Ref branch1 = geogig.command(BranchCreateOp.class).setName("branch1").call();
        insertAndAdd(points2);
        CheckoutResult result = geogig.command(CheckoutOp.class).setSource("branch1").setForce(true)
                .call();

        assertEquals(c1.getTreeId(), result.getNewTree());
        assertTrue(geogig.command(RefParse.class).setName(Ref.HEAD).call().get() instanceof SymRef);
        assertEquals(branch1.getName(),
                ((SymRef) geogig.command(RefParse.class).setName(Ref.HEAD).call().get())
                        .getTarget());
    }

    @Test
    public void testCheckoutPathFilterToUpdatePathThatIsntInIndex() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("commit 1").call();

        insertAndAdd(points2);
        geogig.command(CommitOp.class).setMessage("commit 2").call();

        insertAndAdd(points3);
        geogig.command(CommitOp.class).setMessage("commit 3").call();

        geogig.command(BranchCreateOp.class).setAutoCheckout(true).setName("branch1").call();

        insertAndAdd(lines1);
        geogig.command(CommitOp.class).setMessage("commit 4").call();

        insertAndAdd(lines2);
        geogig.command(CommitOp.class).setMessage("commit 5").call();

        insertAndAdd(lines3);
        geogig.command(CommitOp.class).setMessage("commit 6").call();

        geogig.command(CheckoutOp.class).setSource("master").call();

        CheckoutResult result = geogig.command(CheckoutOp.class).setSource("branch1")
                .addPath("Lines/Lines.1").call();

        Optional<RevTree> workTree = geogig.command(RevObjectParse.class)
                .setObjectId(result.getNewTree()).call(RevTree.class);

        Optional<NodeRef> nodeRef = geogig.command(FindTreeChild.class).setParent(workTree.get())
                .setChildPath("Points/Points.1").call();
        assertTrue(nodeRef.isPresent());

        nodeRef = geogig.command(FindTreeChild.class).setParent(workTree.get())
                .setChildPath("Points/Points.2").call();
        assertTrue(nodeRef.isPresent());

        nodeRef = geogig.command(FindTreeChild.class).setParent(workTree.get())
                .setChildPath("Points/Points.3").call();
        assertTrue(nodeRef.isPresent());

        nodeRef = geogig.command(FindTreeChild.class).setParent(workTree.get())
                .setChildPath("Lines/Lines.1").call();
        assertTrue(nodeRef.isPresent());

        nodeRef = geogig.command(FindTreeChild.class).setParent(workTree.get())
                .setChildPath("Lines/Lines.2").call();
        assertFalse(nodeRef.isPresent());

        nodeRef = geogig.command(FindTreeChild.class).setParent(workTree.get())
                .setChildPath("Lines/Lines.3").call();
        assertFalse(nodeRef.isPresent());
    }

    @Test
    public void testCheckoutPathDuringConflict() throws Exception {
        createConflictedState();
        String path = NodeRef.appendChild(pointsName, idP1);
        try {
            geogig.command(CheckoutOp.class).addPath(path).call();
        } catch (CheckoutException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testCheckoutBranchDuringConflict() throws Exception {
        createConflictedState();
        try {
            geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        } catch (CheckoutException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testCheckoutOursAndBranchDuringConflict() throws Exception {
        createConflictedState();
        try {
            geogig.command(CheckoutOp.class).setSource("TestBranch").setOurs(true).call();
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testCheckoutForceDuringConflict() throws Exception {
        createConflictedState();
        String path = NodeRef.appendChild(pointsName, idP1);
        String path2 = NodeRef.appendChild(pointsName, idP1);
        geogig.command(CheckoutOp.class).addPath(path).addPath(path2).setForce(true).call();
    }

    @Test
    public void testCheckoutOursAndTheirs() throws Exception {
        try {
            geogig.command(CheckoutOp.class).setOurs(true).setTheirs(true).addPath("dummypath")
                    .call();
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testCheckoutOurs() throws Exception {
        createConflictedState();
        String path = NodeRef.appendChild(pointsName, idP1);
        geogig.command(CheckoutOp.class).addPath(path).setOurs(true).call();
        Optional<Node> node = geogig.getRepository().workingTree().findUnstaged(path);
        String headPath = Ref.HEAD + ":" + path;
        Optional<ObjectId> id = geogig.command(RevParse.class).setRefSpec(headPath).call();
        assertEquals(id.get(), node.get().getObjectId());
    }

    @Test
    public void testCheckoutOursDeleted() throws Exception {
        createDeleteOursConflictedState();
        String path = NodeRef.appendChild(pointsName, idP1);
        geogig.command(CheckoutOp.class).addPath(path).setOurs(true).call();
        Optional<Node> node = geogig.getRepository().index().findStaged(path);
        assertFalse(node.isPresent());
        String headPath = Ref.HEAD + ":" + path;
        Optional<ObjectId> id = geogig.command(RevParse.class).setRefSpec(headPath).call();
        assertFalse(id.isPresent());
    }

    @Test
    public void testCheckoutTheirs() throws Exception {
        createConflictedState();
        String path = NodeRef.appendChild(pointsName, idP1);
        geogig.command(CheckoutOp.class).addPath(path).setTheirs(true).call();
        Optional<Node> node = geogig.getRepository().workingTree().findUnstaged(path);
        String headPath = Ref.MERGE_HEAD + ":" + path;
        Optional<ObjectId> id = geogig.command(RevParse.class).setRefSpec(headPath).call();
        assertEquals(id.get(), node.get().getObjectId());
    }

    @Test
    public void testCheckoutTheirsDeleted() throws Exception {
        createDeleteTheirsConflictedState();
        String path = NodeRef.appendChild(pointsName, idP1);
        geogig.command(CheckoutOp.class).addPath(path).setTheirs(true).call();
        Optional<Node> node = geogig.getRepository().index().findStaged(path);
        assertFalse(node.isPresent());
        String headPath = Ref.MERGE_HEAD + ":" + path;
        Optional<ObjectId> id = geogig.command(RevParse.class).setRefSpec(headPath).call();
        assertFalse(id.isPresent());
    }

    private void createConflictedState() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - TestBranch - Points 1 modified and points 2 added
        // |
        // o - master - HEAD - Points 1 modifiedB
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
            geogig.command(MergeOp.class).addCommit(branch.getObjectId()).call();
            fail();
        } catch (MergeConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }
    }

    private void createDeleteTheirsConflictedState() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - TestBranch - Points 1 deleted and points 2 added
        // |
        // o - master - HEAD - Points 1 modified
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        insertAndAdd(points1Modified);
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        geogig.command(RemoveOp.class).addPathToRemove(NodeRef.appendChild(pointsName, idP1))
                .call();
        insertAndAdd(points2);
        geogig.command(CommitOp.class).call();

        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        try {
            geogig.command(MergeOp.class).addCommit(branch.getObjectId()).call();
            fail();
        } catch (MergeConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }
    }

    private void createDeleteOursConflictedState() throws Exception {
        // Create the following revision graph
        // o
        // |
        // o - Points 1 added
        // |\
        // | o - TestBranch - Points 1 deleted and points 2 added
        // |
        // o - master - HEAD - Points 1 modified
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        geogig.command(BranchCreateOp.class).setName("TestBranch").call();
        geogig.command(RemoveOp.class).addPathToRemove(NodeRef.appendChild(pointsName, idP1))
                .call();
        geogig.command(CommitOp.class).call();
        geogig.command(CheckoutOp.class).setSource("TestBranch").call();
        insertAndAdd(points1ModifiedB);
        insertAndAdd(points2);
        geogig.command(CommitOp.class).call();

        geogig.command(CheckoutOp.class).setSource("master").call();
        Ref branch = geogig.command(RefParse.class).setName("TestBranch").call().get();
        try {
            geogig.command(MergeOp.class).addCommit(branch.getObjectId()).call();
            fail();
        } catch (MergeConflictsException e) {
            assertTrue(e.getMessage().contains("conflict"));
        }
    }

}
