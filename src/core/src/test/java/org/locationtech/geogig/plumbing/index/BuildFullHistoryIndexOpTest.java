/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.plumbing.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.RemoveOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;

public class BuildFullHistoryIndexOpTest extends RepositoryTestCase {

    private IndexDatabase indexdb;

    private Node worldPointsLayer;

    private IndexInfo indexInfo;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        Repository repository = getRepository();
        indexdb = repository.indexDatabase();
        worldPointsLayer = IndexTestSupport.createWorldPointsLayer(repository).getNode();
        super.add();
        super.commit("created world points layer");
        String fid1 = IndexTestSupport.getPointFid(5, 10);
        repository.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid1)).call();
        repository.command(BranchCreateOp.class).setName("branch1").call();
        super.add();
        super.commit("deleted 5, 10");
        String fid2 = IndexTestSupport.getPointFid(35, -40);
        repository.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid2)).call();
        super.add();
        super.commit("deleted 35, -40");
        repository.command(CheckoutOp.class).setSource("branch1").call();
        String fid3 = IndexTestSupport.getPointFid(-10, 65);
        repository.command(RemoveOp.class)
                .addPathToRemove(NodeRef.appendChild(worldPointsLayer.getName(), fid3)).call();
        super.add();
        super.commit("deleted -10, 65");
        repository.command(CheckoutOp.class).setSource("master").call();

        assertNotEquals(RevTree.EMPTY_TREE_ID, worldPointsLayer.getObjectId());
    }

    private IndexInfo createIndex(@Nullable String... extraAttributes) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(IndexInfo.MD_QUAD_MAX_BOUNDS, new Envelope(-180, 180, -90, 90));
        if (extraAttributes != null && extraAttributes.length > 0) {
            metadata.put(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA, extraAttributes);
        }
        IndexInfo indexInfo;
        indexInfo = indexdb.createIndexInfo(worldPointsLayer.getName(), "geom", IndexType.QUADTREE,
                metadata);
        return indexInfo;
    }

    @Test
    public void testBuildFullHistory() {
        indexInfo = createIndex();
        int treesUpdated = geogig.command(BuildFullHistoryIndexOp.class)//
                .setTreeRefSpec(indexInfo.getTreeName())//
                .setAttributeName(indexInfo.getAttributeName())//
                .call();

        assertEquals(4, treesUpdated);

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:" + worldPointsLayer.getName()).call().get();
        Optional<ObjectId> indexedTreeId = indexdb.resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId);

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD~1:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId);

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD~2:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId);

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("branch1:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId);
    }

    @Test
    public void testBuildFullHistoryNoAttributeName() {
        indexInfo = createIndex("x", "xystr");
        int treesUpdated = geogig.command(BuildFullHistoryIndexOp.class)//
                .setTreeRefSpec(indexInfo.getTreeName())//
                .call();

        assertEquals(4, treesUpdated);

        ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD:" + worldPointsLayer.getName()).call().get();
        Optional<ObjectId> indexedTreeId = indexdb.resolveIndexedTree(indexInfo,
                canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId, "x",
                "xystr");

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD~1:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId, "x",
                "xystr");

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("HEAD~2:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId, "x",
                "xystr");

        canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish("branch1:" + worldPointsLayer.getName()).call().get();
        indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
        assertTrue(indexedTreeId.isPresent());

        IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(), canonicalFeatureTreeId, "x",
                "xystr");
    }

    @Test
    public void testBuildFullHistoryNoTreeName() {
        indexInfo = createIndex();

        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Tree ref spec not provided");
        geogig.command(BuildFullHistoryIndexOp.class).call();
    }

    @Test
    public void testBuildFullHistoryNoIndex() {
        exception.expect(IllegalStateException.class);
        exception.expectMessage("A matching index could not be found.");
        geogig.command(BuildFullHistoryIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .call();
    }

    @Test
    public void testBuildFullHistoryNoMatchingIndex() {
        indexdb.createIndexInfo(worldPointsLayer.getName(), "x", IndexType.QUADTREE, null);
        exception.expect(IllegalStateException.class);
        exception.expectMessage("A matching index could not be found.");
        geogig.command(BuildFullHistoryIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setAttributeName("y")//
                .call();
    }

    @Test
    public void testBuildFullHistoryMultipleMatchingIndexes() {
        indexdb.createIndexInfo(worldPointsLayer.getName(), "x", IndexType.QUADTREE, null);
        indexdb.createIndexInfo(worldPointsLayer.getName(), "y", IndexType.QUADTREE, null);
        exception.expect(IllegalStateException.class);
        exception.expectMessage(
                "Multiple indexes were found for the specified tree, please specify the attribute.");
        geogig.command(BuildFullHistoryIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .call();
    }

    @Test
    public void testConcurrentlyBuildingSameIndex() throws Exception {
        indexInfo = createIndex();
        final int threadCount = 4;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            Future<?> future = executor.submit(new Runnable() {
                @Override
                public void run() {
                    geogig.command(BuildFullHistoryIndexOp.class)//
                            .setTreeRefSpec(indexInfo.getTreeName())//
                            .setAttributeName(indexInfo.getAttributeName())//
                            .call();

                    ObjectId canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                            .setTreeish("HEAD:" + worldPointsLayer.getName()).call().get();
                    Optional<ObjectId> indexedTreeId = indexdb.resolveIndexedTree(indexInfo,
                            canonicalFeatureTreeId);
                    assertTrue(indexedTreeId.isPresent());

                    IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(),
                            canonicalFeatureTreeId);

                    canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                            .setTreeish("HEAD~1:" + worldPointsLayer.getName()).call().get();
                    indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
                    assertTrue(indexedTreeId.isPresent());

                    IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(),
                            canonicalFeatureTreeId);

                    canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                            .setTreeish("HEAD~2:" + worldPointsLayer.getName()).call().get();
                    indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
                    assertTrue(indexedTreeId.isPresent());

                    IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(),
                            canonicalFeatureTreeId);

                    canonicalFeatureTreeId = geogig.command(ResolveTreeish.class)
                            .setTreeish("branch1:" + worldPointsLayer.getName()).call().get();
                    indexedTreeId = indexdb.resolveIndexedTree(indexInfo, canonicalFeatureTreeId);
                    assertTrue(indexedTreeId.isPresent());

                    IndexTestSupport.verifyIndex(geogig, indexedTreeId.get(),
                            canonicalFeatureTreeId);
                }
            });
            futures.add(future);
        }

        for (Future<?> f : futures) {
            f.get();
        }
    }

}
