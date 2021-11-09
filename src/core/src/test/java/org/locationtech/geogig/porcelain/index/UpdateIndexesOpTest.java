/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.porcelain.index;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.plumbing.index.IndexTestSupport.getPointFid;

import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Test;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.index.IndexTestSupport;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.CherryPickOp;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.porcelain.RebaseConflictsException;
import org.locationtech.geogig.porcelain.RebaseOp;
import org.locationtech.geogig.porcelain.RemoveOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.locationtech.geogig.transaction.GeogigTransaction;
import org.locationtech.geogig.transaction.TransactionBegin;
import org.locationtech.geogig.transaction.TransactionEnd;

import com.google.common.collect.Lists;

public class UpdateIndexesOpTest extends RepositoryTestCase {

    private IndexDatabase indexdb;

    private Node worldPointsLayer;

    protected @Override void setUpInternal() throws Exception {
        Repository repository = getRepository();
        indexdb = repository.context().indexDatabase();
        worldPointsLayer = IndexTestSupport.createWorldPointsLayer(repository).getNode();
        super.add();
        super.commit("created world points layer");
        String fid1 = getPointFid(0, 0);
        repository.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid1)).call();
        repository.command(BranchCreateOp.class).setName("branch1").call();
        super.add();
        super.commit("deleted 0, 0");
        String fid2 = getPointFid(0, 5);
        repository.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid2)).call();
        super.add();
        super.commit("deleted 0, 5");
        repository.command(CheckoutOp.class).setSource("branch1").call();
        String fid3 = getPointFid(0, 10);
        repository.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid3)).call();
        super.add();
        super.commit("deleted 0, 10");
        repository.command(CheckoutOp.class).setSource("master").call();

        assertNotEquals(RevTree.EMPTY_TREE_ID, worldPointsLayer.getObjectId());
    }

    private IndexInfo createIndex(boolean indexHistory, @Nullable String... extraAttributes) {
        Index index = repo.command(CreateQuadTree.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setGeometryAttributeName("geom")//
                .setExtraAttributes(Lists.newArrayList(extraAttributes))//
                .setIndexHistory(indexHistory)//
                .call();
        return index.info();
    }

    @Test
    public void testUpdateIndexesHook() {
        IndexInfo indexInfo = createIndex(true, "x");

        String fid = IndexTestSupport.getPointFid(5, 5);
        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit = commit("deleted 5, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                commit.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> commitIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId(),
                "x");
    }

    @Test
    public void testUpdateIndexesHookBranch() {
        IndexInfo indexInfo = createIndex(true, "x", "y");

        String fid = IndexTestSupport.getPointFid(5, 5);
        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit = commit("deleted 5, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                commit.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> commitIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId(),
                "x", "y");

        branch("testbranch1");
        checkout("testbranch1");

        fid = IndexTestSupport.getPointFid(10, 5);
        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        commit = commit("deleted 10, 5");
        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                commit.getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId(),
                "x", "y");
    }

    @Test
    public void testUpdateIndexesHookBranchNoFullHistory() {

        String fid = getPointFid(5, 5);
        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit1 = commit("deleted 5, 5");

        fid = getPointFid(10, 5);
        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit2 = commit("deleted 10, 5");

        IndexInfo indexInfo = createIndex(false);

        // New index tree should be created for head commit.
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                commit2.getId() + ":" + worldPointsLayer.getName());
        Optional<ObjectId> commitIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId());

        // There shouldn't be an index for the first commit
        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                commit1.getId() + ":" + worldPointsLayer.getName());
        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertFalse(commitIndex.isPresent());

        // Create a branch on the first commit
        repo.command(BranchCreateOp.class).setName("testbranch1")
                .setSource(commit1.getId().toString()).call();

        // Index should be created for new branch commit
        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId());
    }

    @Test
    public void testUpdateIndexesHookMerge() {
        IndexInfo indexInfo = createIndex(true);

        branch("testbranch1");

        String fid = IndexTestSupport.getPointFid(5, 5);
        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit = commit("deleted 5, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                commit.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> commitIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId());

        checkout("testbranch1");

        fid = IndexTestSupport.getPointFid(10, 5);
        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        commit = commit("deleted 10, 5");
        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                commit.getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId());

        checkout("master");

        MergeReport report = repo.command(MergeOp.class).addCommit(commit.getId())
                .setMessage("merged branch1").call();
        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                report.getMergeCommit().getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId());
    }

    @Test
    public void testUpdateIndexesHookMergeConflicts() throws Exception {
        IndexInfo indexInfo = createIndex(true);

        branch("testbranch1");

        String fid = getPointFid(10, 5);
        Feature modified = feature(IndexTestSupport.featureType, fid, "POINT(1 1)",
                Double.valueOf(1), Double.valueOf(1), "1,1");
        insertAndAdd(modified);
        RevCommit commit = commit("modified 10, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                commit.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> commitIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId());

        checkout("testbranch1");

        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        commit = commit("deleted 10, 5");
        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                commit.getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId());

        checkout("master");

        try {
            repo.command(MergeOp.class).addCommit(commit.getId()).setMessage("merged branch1")
                    .call();
            fail();
        } catch (MergeConflictsException e) {
            // expected
        }

        // resolve conflict
        Feature resolved = feature(IndexTestSupport.featureType, fid, "POINT(2 1)",
                Double.valueOf(2), Double.valueOf(1), "2,1");
        insertAndAdd(resolved);

        // commit
        RevCommit mergeCommit = repo.command(CommitOp.class).call();
        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                mergeCommit.getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId());
    }

    @Test
    public void testUpdateIndexesHookRebase() {
        IndexInfo indexInfo = createIndex(true, "xystr");

        branch("testbranch1");

        String fid = IndexTestSupport.getPointFid(5, 5);
        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit masterCommit = commit("deleted 5, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                masterCommit.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> masterIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(masterIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), masterIndex.get(), featureTree.getObjectId(),
                "xystr");

        checkout("testbranch1");

        fid = IndexTestSupport.getPointFid(10, 5);
        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit branchCommit = commit("deleted 10, 5");
        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                branchCommit.getId() + ":" + worldPointsLayer.getName());

        Optional<ObjectId> branchIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(branchIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), branchIndex.get(), featureTree.getObjectId(),
                "xystr");

        fid = IndexTestSupport.getPointFid(10, 10);
        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit branchCommit2 = commit("deleted 10, 10");
        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                branchCommit2.getId() + ":" + worldPointsLayer.getName());

        Optional<ObjectId> branchIndex2 = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(branchIndex2.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), branchIndex2.get(), featureTree.getObjectId(),
                "xystr");

        // rebase branch1 onto master
        repo.command(RebaseOp.class).setUpstream(masterCommit::getId).call();

        // verify that both rebased commits were indexed
        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                "testbranch1~1:" + worldPointsLayer.getName());

        Optional<ObjectId> rebasedBranchIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(rebasedBranchIndex.isPresent());

        assertNotEquals(branchIndex.get(), rebasedBranchIndex.get());

        IndexTestSupport.verifyIndex(repo.context(), rebasedBranchIndex.get(),
                featureTree.getObjectId(), "xystr");

        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                "testbranch1:" + worldPointsLayer.getName());

        Optional<ObjectId> rebasedBranchIndex2 = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(rebasedBranchIndex2.isPresent());

        assertNotEquals(branchIndex2.get(), rebasedBranchIndex2.get());

        IndexTestSupport.verifyIndex(repo.context(), rebasedBranchIndex2.get(),
                featureTree.getObjectId(), "xystr");
    }

    @Test
    public void testUpdateIndexesHookRebaseConflicts() throws Exception {
        IndexInfo indexInfo = createIndex(true, "xystr");

        branch("testbranch1");

        String fid = getPointFid(10, 5);
        Feature modified = feature(IndexTestSupport.featureType, fid, "POINT(1 1)",
                Double.valueOf(1), Double.valueOf(1), "1,1");
        insertAndAdd(modified);
        RevCommit masterCommit = commit("modified 10, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                masterCommit.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> masterIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(masterIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), masterIndex.get(), featureTree.getObjectId(),
                "xystr");

        checkout("testbranch1");

        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit branchCommit = commit("deleted 10, 5");
        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                branchCommit.getId() + ":" + worldPointsLayer.getName());

        Optional<ObjectId> branchIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(branchIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), branchIndex.get(), featureTree.getObjectId(),
                "xystr");

        fid = getPointFid(10, 10);
        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit branchCommit2 = commit("deleted 10, 10");
        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                branchCommit2.getId() + ":" + worldPointsLayer.getName());

        Optional<ObjectId> branchIndex2 = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(branchIndex2.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), branchIndex2.get(), featureTree.getObjectId(),
                "xystr");

        // rebase branch1 onto master
        try {
            repo.command(RebaseOp.class).setUpstream(masterCommit::getId).call();
            fail();
        } catch (RebaseConflictsException e) {
            // expected
        }

        // resolve the conflict
        fid = getPointFid(10, 5);
        Feature resolved = feature(IndexTestSupport.featureType, fid, "POINT(2 1)",
                Double.valueOf(2), Double.valueOf(1), "2,1");
        insertAndAdd(resolved);

        // continue rebase
        repo.command(RebaseOp.class).setContinue(true).call();

        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                "testbranch1~1:" + worldPointsLayer.getName());

        Optional<ObjectId> rebasedBranchIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(rebasedBranchIndex.isPresent());

        assertNotEquals(branchIndex.get(), rebasedBranchIndex.get());

        IndexTestSupport.verifyIndex(repo.context(), rebasedBranchIndex.get(),
                featureTree.getObjectId(), "xystr");

        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                "testbranch1:" + worldPointsLayer.getName());

        Optional<ObjectId> rebasedBranchIndex2 = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(rebasedBranchIndex2.isPresent());

        assertNotEquals(branchIndex2.get(), rebasedBranchIndex2.get());

        IndexTestSupport.verifyIndex(repo.context(), rebasedBranchIndex2.get(),
                featureTree.getObjectId(), "xystr");
    }

    @Test
    public void testUpdateIndexesHookCherryPick() {
        IndexInfo indexInfo = createIndex(true);

        branch("testbranch1");

        String fid = getPointFid(5, 5);
        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit1 = commit("deleted 5, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                commit1.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> commitIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId());

        checkout("testbranch1");

        fid = getPointFid(10, 5);
        repo.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit2 = commit("deleted 10, 5");
        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                commit2.getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId());

        checkout("master");

        RevCommit cherryPicked = repo.command(CherryPickOp.class).setCommit(commit2::getId).call();
        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                cherryPicked.getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId());
    }

    @Test
    public void testUpdateIndexesHookTransaction() {
        IndexInfo indexInfo = createIndex(true, "x");

        GeogigTransaction transaction = repo.command(TransactionBegin.class).call();

        String fid = getPointFid(5, 5);
        transaction.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        transaction.command(AddOp.class).call();
        RevCommit commit = transaction.command(CommitOp.class).setMessage("deleted 5,5").call();
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(transaction,
                commit.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> commitIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(transaction, commitIndex.get(), featureTree.getObjectId(),
                "x");

        // end the transaction
        repo.command(TransactionEnd.class).setTransaction(transaction).call();

        featureTree = IndexUtils.resolveTypeTreeRef(repo.context(),
                "HEAD:" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(repo.context(), commitIndex.get(), featureTree.getObjectId(),
                "x");
    }
}
