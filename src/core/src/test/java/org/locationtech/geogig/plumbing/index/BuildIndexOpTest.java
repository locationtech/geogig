package org.locationtech.geogig.plumbing.index;

import static org.locationtech.geogig.model.impl.RevObjectTestSupport.getTreeNodes;
import static org.locationtech.geogig.plumbing.index.QuadTreeTestSupport.createPointFeature;
import static org.locationtech.geogig.plumbing.index.QuadTreeTestSupport.createWorldPointsLayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.model.impl.RevFeatureBuilder;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.IndexInfo.IndexType;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.simple.SimpleFeature;

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
        return createIndexForLayer(worldPointsLayer.getName(), extraAttributes);
    }

    private IndexInfo createIndexForLayer(String layerName, @Nullable String... extraAttributes) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(IndexInfo.MD_QUAD_MAX_BOUNDS, new Envelope(-180, 180, -90, 90));
        if (extraAttributes != null && extraAttributes.length > 0) {
            metadata.put(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA, extraAttributes);
        }
        IndexInfo indexInfo;
        indexInfo = indexdb.createIndexInfo(layerName, "geom", IndexType.QUADTREE, metadata);
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

        IndexTestSupport.verifyIndex(geogig, indexTree.getId(), newCanonicalTree.getId());
    }

    @Test
    public void testCreatesMaterializedIndex() {
        indexInfo = createIndex("x", "y", "xystr");
        final RevTree oldCanonicalTree = RevTree.EMPTY;
        final RevTree newCanonicalTree = worldPointsTree;

        RevTree indexTree = updateIndex(oldCanonicalTree, newCanonicalTree);

        assertNotEquals(RevTree.EMPTY, indexTree);
        assertEquals(newCanonicalTree.size(), indexTree.size());

        IndexTestSupport.verifyIndex(geogig, indexTree.getId(), newCanonicalTree.getId(), "x", "y",
                "xystr");
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
        RevTree newCanonicalTree = checkUpdatesNodes();
        Optional<ObjectId> indexId = indexdb.resolveIndexedTree(indexInfo,
                newCanonicalTree.getId());
        assertTrue(indexId.isPresent());
        IndexTestSupport.verifyIndex(geogig, indexId.get(), newCanonicalTree.getId());
    }

    @Test
    public void testUpdatesMaterializedNodes() {
        indexInfo = createIndex("x", "y", "xystr");
        RevTree newCanonicalTree = checkUpdatesNodes();
        Optional<ObjectId> indexId = indexdb.resolveIndexedTree(indexInfo,
                newCanonicalTree.getId());
        assertTrue(indexId.isPresent());
        IndexTestSupport.verifyIndex(geogig, indexId.get(), newCanonicalTree.getId(), "x", "y",
                "xystr");
    }

    @Test
    public void testSupportsDuplicatedData() throws Exception {
        testSupportsDuplicatedData(null);
    }

    @Test
    public void testSupportsDuplicatedDataMaterializedNodes() throws Exception {
        testSupportsDuplicatedData("sp", "ip");
    }

    public void testSupportsDuplicatedData(String... extraAttributes) throws Exception {
        insertAndAdd(points1, points2, points3);
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(pointsType);
        featureBuilder.addAll(((SimpleFeature) points1).getAttributes());
        SimpleFeature duplicateFeature = featureBuilder.buildFeature("duplcate-contents");
        insertAndAdd(duplicateFeature);

        assertEquals(RevFeatureBuilder.build(points1), RevFeatureBuilder.build(duplicateFeature));
        commit("points, two features pointing to the same RevFeature");

        List<NodeRef> featureTypeTrees = repo.workingTree().getFeatureTypeTrees();
        NodeRef typeTree = null;
        for (NodeRef nr : featureTypeTrees) {
            if (pointsName.equals(nr.path())) {
                typeTree = nr;
                break;
            }
        }
        assertNotNull(typeTree);

        ObjectId featureTypeId = typeTree.getMetadataId();
        ObjectId treeId = typeTree.getObjectId();

        RevTree canonicalTree = repo.getTree(treeId);
        assertEquals(4, canonicalTree.size());

        IndexInfo indexInfo = createIndexForLayer(pointsName, extraAttributes);
        RevTree indexTree = updateIndex(indexInfo, featureTypeId, RevTree.EMPTY, canonicalTree);
        assertEquals(canonicalTree.size(), indexTree.size());
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
        return newCanonicalTree;
    }

    private RevTree createIndexFor(final RevTree canonicalTree) {
        return updateIndex(RevTree.EMPTY, canonicalTree);
    }

    private RevTree updateIndex(final RevTree oldCanonicalTree, final RevTree newCanonicalTree) {
        ObjectId featureTypeId = worldPointsLayer.getMetadataId().get();
        return updateIndex(this.indexInfo, featureTypeId, oldCanonicalTree, newCanonicalTree);
    }

    private RevTree updateIndex(IndexInfo indexInfo, ObjectId featureTypeId,
            final RevTree oldCanonicalTree, final RevTree newCanonicalTree) {

        Repository repo = getRepository();
        BuildIndexOp command = repo.command(BuildIndexOp.class);
        command.setIndex(indexInfo);
        command.setOldCanonicalTree(oldCanonicalTree);
        command.setNewCanonicalTree(newCanonicalTree);
        command.setRevFeatureTypeId(featureTypeId);

        RevTree indexTree = command.call();
        assertEquals(newCanonicalTree.size(), indexTree.size());
        Optional<ObjectId> resolveIndexedTree = indexdb.resolveIndexedTree(indexInfo,
                newCanonicalTree.getId());
        assertTrue(resolveIndexedTree.isPresent());
        assertEquals(indexTree.getId(), resolveIndexedTree.get());
        return indexTree;
    }
}
