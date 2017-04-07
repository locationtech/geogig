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

import static org.locationtech.geogig.plumbing.index.IndexTestSupport.getPointFid;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.plumbing.TransactionEnd;
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
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Feature;

import com.google.common.base.Optional;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;

public class UpdateIndexesOpTest extends RepositoryTestCase {

    private IndexDatabase indexdb;

    private Node worldPointsLayer;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        Repository repository = getRepository();
        indexdb = repository.indexDatabase();
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
        Index index = geogig.command(CreateQuadTree.class)//
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
        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit = commit("deleted 5, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                commit.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> commitIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId(), "x");
    }

    @Test
    public void testUpdateIndexesHookBranch() {
        IndexInfo indexInfo = createIndex(true, "x", "y");

        String fid = IndexTestSupport.getPointFid(5, 5);
        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit = commit("deleted 5, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                commit.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> commitIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId(), "x",
                "y");

        branch("testbranch1");
        checkout("testbranch1");

        fid = IndexTestSupport.getPointFid(10, 5);
        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        commit = commit("deleted 10, 5");
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                commit.getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId(), "x",
                "y");
    }

    @Test
    public void testUpdateIndexesHookBranchNoFullHistory() {

        String fid = getPointFid(5, 5);
        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit1 = commit("deleted 5, 5");

        fid = getPointFid(10, 5);
        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit2 = commit("deleted 10, 5");

        IndexInfo indexInfo = createIndex(false);

        // New index tree should be created for head commit.
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                commit2.getId() + ":" + worldPointsLayer.getName());
        Optional<ObjectId> commitIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId());

        // There shouldn't be an index for the first commit
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                commit1.getId() + ":" + worldPointsLayer.getName());
        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertFalse(commitIndex.isPresent());

        // Create a branch on the first commit
        geogig.command(BranchCreateOp.class).setName("testbranch1")
                .setSource(commit1.getId().toString()).call();

        // Index should be created for new branch commit
        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId());
    }

    @Test
    public void testUpdateIndexesHookMerge() {
        IndexInfo indexInfo = createIndex(true);

        branch("testbranch1");

        String fid = IndexTestSupport.getPointFid(5, 5);
        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit = commit("deleted 5, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                commit.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> commitIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId());

        checkout("testbranch1");

        fid = IndexTestSupport.getPointFid(10, 5);
        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        commit = commit("deleted 10, 5");
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                commit.getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId());

        checkout("master");

        MergeReport report = geogig.command(MergeOp.class).addCommit(commit.getId())
                .setMessage("merged branch1").call();
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                report.getMergeCommit().getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId());
    }

    @Test
    public void testUpdateIndexesHookMergeConflicts() throws Exception {
        IndexInfo indexInfo = createIndex(true);

        branch("testbranch1");

        String fid = getPointFid(10, 5);
        Feature modified = feature(IndexTestSupport.featureType, fid, "POINT(1 1)",
                new Double(1), new Double(1), "1,1");
        insertAndAdd(modified);
        RevCommit commit = commit("modified 10, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                commit.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> commitIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId());

        checkout("testbranch1");

        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        commit = commit("deleted 10, 5");
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                commit.getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId());

        checkout("master");

        try {
            geogig.command(MergeOp.class).addCommit(commit.getId())
                    .setMessage("merged branch1").call();
            fail();
        } catch (MergeConflictsException e) {
            // expected
        }

        // resolve conflict
        Feature resolved = feature(IndexTestSupport.featureType, fid, "POINT(2 1)",
                new Double(2), new Double(1), "2,1");
        insertAndAdd(resolved);

        // commit
        RevCommit mergeCommit = geogig.command(CommitOp.class).call();
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                mergeCommit.getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId());
    }

    @Test
    public void testUpdateIndexesHookRebase() {
        IndexInfo indexInfo = createIndex(true, "xystr");

        branch("testbranch1");

        String fid = IndexTestSupport.getPointFid(5, 5);
        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit masterCommit = commit("deleted 5, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                masterCommit.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> masterIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(masterIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, masterIndex.get(), featureTree.getObjectId(), "xystr");

        checkout("testbranch1");

        fid = IndexTestSupport.getPointFid(10, 5);
        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit branchCommit = commit("deleted 10, 5");
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                branchCommit.getId() + ":" + worldPointsLayer.getName());

        Optional<ObjectId> branchIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(branchIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, branchIndex.get(), featureTree.getObjectId(), "xystr");

        fid = IndexTestSupport.getPointFid(10, 10);
        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit branchCommit2 = commit("deleted 10, 10");
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                branchCommit2.getId() + ":" + worldPointsLayer.getName());

        Optional<ObjectId> branchIndex2 = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(branchIndex2.isPresent());

        IndexTestSupport.verifyIndex(geogig, branchIndex2.get(), featureTree.getObjectId(),
                "xystr");

        // rebase branch1 onto master
        geogig.command(RebaseOp.class).setUpstream(Suppliers.ofInstance(masterCommit.getId()))
                .call();

        // verify that both rebased commits were indexed
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                "testbranch1~1:" + worldPointsLayer.getName());

        Optional<ObjectId> rebasedBranchIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(rebasedBranchIndex.isPresent());

        assertNotEquals(branchIndex.get(), rebasedBranchIndex.get());

        IndexTestSupport.verifyIndex(geogig, rebasedBranchIndex.get(), featureTree.getObjectId(),
                "xystr");

        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                "testbranch1:" + worldPointsLayer.getName());

        Optional<ObjectId> rebasedBranchIndex2 = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(rebasedBranchIndex2.isPresent());

        assertNotEquals(branchIndex2.get(), rebasedBranchIndex2.get());

        IndexTestSupport.verifyIndex(geogig, rebasedBranchIndex2.get(), featureTree.getObjectId(),
                "xystr");
    }

    @Test
    public void testUpdateIndexesHookRebaseConflicts() throws Exception {
        IndexInfo indexInfo = createIndex(true, "xystr");

        branch("testbranch1");

        String fid = getPointFid(10, 5);
        Feature modified = feature(IndexTestSupport.featureType, fid, "POINT(1 1)",
                new Double(1), new Double(1), "1,1");
        insertAndAdd(modified);
        RevCommit masterCommit = commit("modified 10, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                masterCommit.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> masterIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(masterIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, masterIndex.get(), featureTree.getObjectId(), "xystr");

        checkout("testbranch1");

        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit branchCommit = commit("deleted 10, 5");
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                branchCommit.getId() + ":" + worldPointsLayer.getName());

        Optional<ObjectId> branchIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(branchIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, branchIndex.get(), featureTree.getObjectId(), "xystr");

        fid = getPointFid(10, 10);
        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit branchCommit2 = commit("deleted 10, 10");
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                branchCommit2.getId() + ":" + worldPointsLayer.getName());

        Optional<ObjectId> branchIndex2 = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(branchIndex2.isPresent());

        IndexTestSupport.verifyIndex(geogig, branchIndex2.get(), featureTree.getObjectId(),
                "xystr");

        // rebase branch1 onto master
        try {
            geogig.command(RebaseOp.class).setUpstream(Suppliers.ofInstance(masterCommit.getId()))
                    .call();
            fail();
        } catch (RebaseConflictsException e) {
            // expected
        }

        // resolve the conflict
        fid = getPointFid(10, 5);
        Feature resolved = feature(IndexTestSupport.featureType, fid, "POINT(2 1)",
                new Double(2), new Double(1), "2,1");
        insertAndAdd(resolved);

        // continue rebase
        geogig.command(RebaseOp.class).setContinue(true).call();

        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                "testbranch1~1:" + worldPointsLayer.getName());

        Optional<ObjectId> rebasedBranchIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(rebasedBranchIndex.isPresent());

        assertNotEquals(branchIndex.get(), rebasedBranchIndex.get());

        IndexTestSupport.verifyIndex(geogig, rebasedBranchIndex.get(), featureTree.getObjectId(),
                "xystr");

        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                "testbranch1:" + worldPointsLayer.getName());

        Optional<ObjectId> rebasedBranchIndex2 = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(rebasedBranchIndex2.isPresent());

        assertNotEquals(branchIndex2.get(), rebasedBranchIndex2.get());

        IndexTestSupport.verifyIndex(geogig, rebasedBranchIndex2.get(), featureTree.getObjectId(),
                "xystr");
    }

    @Test
    public void testUpdateIndexesHookCherryPick() {
        IndexInfo indexInfo = createIndex(true);

        branch("testbranch1");

        String fid = getPointFid(5, 5);
        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit1 = commit("deleted 5, 5");
        NodeRef featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                commit1.getId() + ":" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        Optional<ObjectId> commitIndex = indexdb.resolveIndexedTree(indexInfo,
                featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId());

        checkout("testbranch1");

        fid = getPointFid(10, 5);
        geogig.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid)).call();
        add();
        RevCommit commit2 = commit("deleted 10, 5");
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                commit2.getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId());

        checkout("master");

        RevCommit cherryPicked = geogig.command(CherryPickOp.class)
                .setCommit(Suppliers.ofInstance(commit2.getId())).call();
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                cherryPicked.getId() + ":" + worldPointsLayer.getName());

        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId());
    }
    
    @Test
    public void testUpdateIndexesHookTransaction() {
        IndexInfo indexInfo = createIndex(true, "x");
        
        GeogigTransaction transaction = geogig.command(TransactionBegin.class).call();

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

        IndexTestSupport.verifyIndex(transaction, commitIndex.get(), featureTree.getObjectId(), "x");
        
        // end the transaction
        geogig.command(TransactionEnd.class).setTransaction(transaction).call();
        
        featureTree = IndexUtils.resolveTypeTreeRef(geogig.getContext(),
                "HEAD:" + worldPointsLayer.getName());

        // New index tree should be automatically created for pre-exising index.
        commitIndex = indexdb.resolveIndexedTree(indexInfo, featureTree.getObjectId());
        assertTrue(commitIndex.isPresent());

        IndexTestSupport.verifyIndex(geogig, commitIndex.get(), featureTree.getObjectId(), "x");
    }
}
