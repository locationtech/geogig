package org.locationtech.geogig.plumbing.index;

import static org.locationtech.geogig.model.impl.RevObjectTestSupport.getTreeNodes;
import static org.locationtech.geogig.plumbing.index.QuadTreeTestSupport.createPointFeature;
import static org.locationtech.geogig.plumbing.index.QuadTreeTestSupport.createWorldPointsLayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.vividsolutions.jts.geom.Envelope;

public class BuildIndexOpTest extends RepositoryTestCase {

    private IndexDatabase indexdb;

    private Node worldPointsLayer;

    private RevTree worldPointsTree;

    private IndexInfo indexInfo;

    @Override
    protected void setUpInternal() throws Exception {
        Repository repository = getRepository();
        indexdb = repository.indexDatabase();
        worldPointsLayer = createWorldPointsLayer(repository);
        super.add();
        super.commit("created world points layer");
        this.worldPointsTree = repository.getTree(worldPointsLayer.getObjectId());
        assertNotEquals(RevTree.EMPTY_TREE_ID, worldPointsLayer.getObjectId());
    }

    private IndexInfo createIndex() {
        return createIndex((String[]) null);
    }

    private IndexInfo createIndex(@Nullable String... extraAttributes) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(IndexInfo.MD_QUAD_MAX_BOUNDS, new Envelope(-180, 180, -90, 90));
        if (extraAttributes != null && extraAttributes.length > 0) {
            metadata.put(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA, extraAttributes);
        }
        IndexInfo indexInfo;
        indexInfo = indexdb.createIndex(worldPointsLayer.getName(), "geom", IndexType.QUADTREE,
                metadata);
        return indexInfo;
    }

    @Test
    public void testCreatesIndex() {
        indexInfo = createIndex();
        final RevTree oldCanonicalTree = RevTree.EMPTY;
        final RevTree newCanonicalTree = worldPointsTree;

        RevTree indexTree = updateIndex(oldCanonicalTree, newCanonicalTree);

        assertNotEquals(RevTree.EMPTY, indexTree);
        assertEquals(newCanonicalTree.size(), indexTree.size());
    }

    @Test
    public void testCreatesMaterializedIndex() {
        indexInfo = createIndex("x", "y", "xystr");
        final RevTree oldCanonicalTree = RevTree.EMPTY;
        final RevTree newCanonicalTree = worldPointsTree;

        RevTree indexTree = updateIndex(oldCanonicalTree, newCanonicalTree);

        assertNotEquals(RevTree.EMPTY, indexTree);
        assertEquals(newCanonicalTree.size(), indexTree.size());

        verifyMaterializedNodes(indexTree);
    }

    @Test
    public void testRemovesAllEntriesFromIndex() {
        indexInfo = createIndex();
        final RevTree oldCanonicalTree = RevTree.EMPTY;
        final RevTree newCanonicalTree = worldPointsTree;

        // first make sure there is an index tree
        createIndexFor(newCanonicalTree);

        // now invert the order and see if the index tree got empty
        RevTree deletedNodesIndexTree = updateIndex(newCanonicalTree, oldCanonicalTree);
        assertEquals(RevTree.EMPTY, deletedNodesIndexTree);
    }

    @Test
    public void testUpdatesNodes() {
        indexInfo = createIndex();
        checkUpdatesNodes();
    }

    @Test
    public void testUpdatesMaterializedNodes() {
        indexInfo = createIndex("x", "y", "xystr");
        RevTree newIndexTree = checkUpdatesNodes();
        verifyMaterializedNodes(newIndexTree);
    }

    private RevTree checkUpdatesNodes() {
        final RevTree oldCanonicalTree = worldPointsTree;
        final RevTree newCanonicalTree;

        // old nodes with x < 0 are moved to -x, y
        final Set<Node> changedNodes = new HashSet<>();
        {
            ObjectStore objectStore = getRepository().objectDatabase();
            Set<Node> oldNodes = getTreeNodes(oldCanonicalTree, objectStore);
            CanonicalTreeBuilder builder = CanonicalTreeBuilder.create(objectStore,
                    oldCanonicalTree);
            for (Node n : oldNodes) {
                Envelope b = n.bounds().get();
                if (b.getMinX() < 0) {
                    double x = -b.getMinX();
                    double y = b.getMinY();
                    Envelope newBounds = new Envelope(x, x, y, y);

                    RevFeature updatedFeature = createPointFeature(x, y, x, y,
                            ("modified: " + x + "," + y));
                    objectStore.put(updatedFeature);

                    Node update = n.update(updatedFeature.getId(), newBounds);
                    changedNodes.add(update);
                    builder.put(update);
                }
            }
            newCanonicalTree = builder.build();
        }

        // first make sure there is an index tree
        RevTree oldIndexTree = createIndexFor(oldCanonicalTree);

        // now invert the order and see if the index tree got empty
        RevTree updatedIndexTree = updateIndex(oldCanonicalTree, newCanonicalTree);

        Set<Node> oldIndexNodes = getTreeNodes(oldIndexTree, indexdb);
        Set<Node> newIndexNodes = getTreeNodes(updatedIndexTree, indexdb);

        SetView<Node> difference = Sets.difference(newIndexNodes, oldIndexNodes);
        assertEquals(changedNodes, difference);
        return updatedIndexTree;
    }

    private RevTree createIndexFor(final RevTree canonicalTree) {
        return updateIndex(RevTree.EMPTY, canonicalTree);
    }

    private RevTree updateIndex(final RevTree oldCanonicalTree, final RevTree newCanonicalTree) {

        Repository repo = getRepository();
        BuildIndexOp command = repo.command(BuildIndexOp.class);
        command.setIndex(indexInfo);
        command.setOldCanonicalTree(oldCanonicalTree);
        command.setNewCanonicalTree(newCanonicalTree);
        command.setRevFeatureTypeId(worldPointsLayer.getMetadataId().get());

        RevTree indexTree = command.call();
        assertEquals(newCanonicalTree.size(), indexTree.size());
        Optional<ObjectId> resolveIndexedTree = indexdb.resolveIndexedTree(indexInfo,
                newCanonicalTree.getId());
        assertTrue(resolveIndexedTree.isPresent());
        assertEquals(indexTree.getId(), resolveIndexedTree.get());
        return indexTree;
    }

    private void verifyMaterializedNodes(RevTree indexTree) {
        Set<Node> nodes = getTreeNodes(indexTree, indexdb);
        assertEquals(indexTree.size(), nodes.size());

        for (Node n : nodes) {
            Map<String, Object> extraData = n.getExtraData();
            assertNotNull("Node has no extra data: " + n, extraData);
            Map<String, Object> values = IndexInfo.getMaterializedAttributes(n);
            assertFalse("Node has no @attributes values: " + n, values.isEmpty());
            assertTrue(values.containsKey("x"));
            assertTrue(values.containsKey("y"));
            assertTrue(values.containsKey("xystr"));
        }
    }

}
