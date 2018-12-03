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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.index.IndexTestSupport;
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

public class DropIndexOpTest extends RepositoryTestCase {

    private IndexDatabase indexdb;

    private Node worldPointsLayer;

    private RevTree worldPointsTree;

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

        this.worldPointsTree = repository.getTree(worldPointsLayer.getObjectId());

        assertNotEquals(RevTree.EMPTY_TREE_ID, worldPointsLayer.getObjectId());
    }

    private IndexInfo createIndex(@Nullable String... extraAttributes) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(IndexInfo.MD_QUAD_MAX_BOUNDS, new Envelope(-180, 180, -90, 90));
        if (extraAttributes != null && extraAttributes.length > 0) {
            metadata.put(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA, extraAttributes);
        }
        Index index = geogig.command(CreateIndexOp.class)//
                .setTreeName(worldPointsLayer.getName())//
                .setCanonicalTypeTree(worldPointsTree)//
                .setFeatureTypeId(worldPointsLayer.getMetadataId().get())//
                .setAttributeName("geom")//
                .setIndexType(IndexType.QUADTREE)//
                .setMetadata(metadata)//
                .call();
        return index.info();
    }

    @Test
    public void testDropIndex() {
        createIndex();

        Optional<IndexInfo> indexInfo = indexdb.getIndexInfo(worldPointsLayer.getName(), "geom");
        assertTrue(indexInfo.isPresent());

        IndexInfo oldIndexInfo = indexInfo.get();

        Optional<ObjectId> indexedTreeId = indexdb.resolveIndexedTree(oldIndexInfo,
                worldPointsTree.getId());
        assertTrue(indexedTreeId.isPresent());

        IndexInfo droppedIndex = geogig.command(DropIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .call();

        assertEquals(oldIndexInfo, droppedIndex);

        indexInfo = indexdb.getIndexInfo(worldPointsLayer.getName(), "geom");
        assertFalse(indexInfo.isPresent());

        indexedTreeId = indexdb.resolveIndexedTree(droppedIndex, worldPointsTree.getId());
        assertFalse(indexedTreeId.isPresent());
    }

    @Test
    public void testDropIndexNoTreeName() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Tree ref spec not provided.");
        geogig.command(DropIndexOp.class).call();
    }

    @Test
    public void testDropIndexWrongTreeName() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Can't find feature tree 'nonexistent'");
        geogig.command(DropIndexOp.class)//
                .setTreeRefSpec("nonexistent")//
                .call();
    }

    @Test
    public void testDropIndexWrongAttributeName() {
        exception.expect(IllegalStateException.class);
        exception.expectMessage("A matching index could not be found.");
        geogig.command(DropIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .setAttributeName("xystr")//
                .call();
    }

    @Test
    public void testDropMultipleMatchingIndexes() {
        indexdb.createIndexInfo(worldPointsLayer.getName(), "x", IndexType.QUADTREE, null);
        indexdb.createIndexInfo(worldPointsLayer.getName(), "y", IndexType.QUADTREE, null);
        exception.expect(IllegalStateException.class);
        exception.expectMessage(
                "Multiple indexes were found for the specified tree, please specify the attribute.");
        geogig.command(DropIndexOp.class)//
                .setTreeRefSpec(worldPointsLayer.getName())//
                .call();
    }

}
