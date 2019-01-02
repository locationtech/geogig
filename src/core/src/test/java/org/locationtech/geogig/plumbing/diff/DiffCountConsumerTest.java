/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.diff;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeNameOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectDatabase;

/**
 *
 */
public class DiffCountConsumerTest extends Assert {

    /**
     * All original feature noderefs have this objectid
     */
    private static final ObjectId FAKE_FEATURE_ID = RevObjectTestSupport
            .hashString("1100000000000000000000000000000000000000");

    /**
     * All changed feature noderefs have this objectid
     */
    private static final ObjectId FAKE_FEATURE_ID_CHANGED = RevObjectTestSupport
            .hashString("2200000000000000000000000000000000000000");

    private ObjectDatabase odb;

    private RevTree childrenFeatureTree;

    /**
     * single level tree with 2 * {@link CanonicalNodeNameOrder#NORMALIZED_SIZE_LIMIT} feature
     * references
     */
    private RevTree bucketsFeatureTree;

    private RevTree childrenFeatureTypesTree;

    @Before
    public void setUp() {
        odb = new HeapObjectDatabase();
        odb.open();
        {
            RevTreeBuilder builder = createFeaturesTree("", 10);
            this.childrenFeatureTree = builder.build();
            odb.put(childrenFeatureTree);
        }

        {
            RevTreeBuilder builder = createFeaturesTree("",
                    2 * CanonicalNodeNameOrder.normalizedSizeLimit(0));
            this.bucketsFeatureTree = builder.build();
            assertNotEquals(0, bucketsFeatureTree.bucketsSize());
            odb.put(bucketsFeatureTree);
        }
    }

    private void createFeatureTypesTree(RevTreeBuilder rootBuilder, String treePath,
            RevTree childTree) {
        odb.put(childTree);
        Node childRef = RevObjectFactory.defaultInstance().createNode(treePath, childTree.getId(),
                ObjectId.NULL, TYPE.TREE, null, null);
        rootBuilder.put(childRef);
    }

    private DiffObjectCount count(RevTree left, RevTree right) {
        PreOrderDiffWalk visitor = new PreOrderDiffWalk(left, right, odb, odb);
        DiffCountConsumer consumer = new DiffCountConsumer(odb);
        visitor.walk(consumer);
        DiffObjectCount count = consumer.get();
        return count;
    }

    @Test
    public void testSameTree() {
        DiffObjectCount count = count(childrenFeatureTree, childrenFeatureTree);
        assertEquals(0, count.featureCount());
        assertEquals(0, count.treeCount());
    }

    @Test
    public void testChildrenEmpty() {
        assertEquals(childrenFeatureTree.size(),
                count(childrenFeatureTree, RevTree.EMPTY).featureCount());
        assertEquals(childrenFeatureTree.size(),
                count(RevTree.EMPTY, childrenFeatureTree).featureCount());
    }

    @Test
    public void testChildrenChildren() {
        CanonicalTreeBuilder builder = CanonicalTreeBuilder.create(odb, childrenFeatureTree);
        RevTree changed = builder.remove("3").build();

        assertEquals(1, count(childrenFeatureTree, changed).featureCount());
        assertEquals(1, count(changed, childrenFeatureTree).featureCount());

        builder = CanonicalTreeBuilder.create(odb, changed);
        builder.put(RevObjectFactory.defaultInstance().createNode("new", FAKE_FEATURE_ID,
                ObjectId.NULL, TYPE.FEATURE, null, null));
        changed = builder.build();

        assertEquals(2, count(childrenFeatureTree, changed).featureCount());
        assertEquals(2, count(changed, childrenFeatureTree).featureCount());

        builder = CanonicalTreeBuilder.create(odb, changed);
        builder.put(RevObjectFactory.defaultInstance().createNode("1", FAKE_FEATURE_ID_CHANGED,
                ObjectId.NULL, TYPE.FEATURE, null, null));
        changed = builder.build();

        assertEquals(3, count(childrenFeatureTree, changed).featureCount());
        assertEquals(3, count(changed, childrenFeatureTree).featureCount());
    }

    @Test
    public void testChildrenChildrenNestedTrees() {
        RevTreeBuilder childTree1;
        RevTreeBuilder childTree2;

        {
            RevTreeBuilder rootBuilder = RevTreeBuilder.builder(odb);
            childTree1 = createFeaturesTree("tree1", 10);
            RevTree child = childTree1.build();
            childTree1 = CanonicalTreeBuilder.create(odb, child);
            createFeatureTypesTree(rootBuilder, "tree1", child);
            childTree2 = createFeaturesTree("tree2", 5);
            child = childTree2.build();
            childTree2 = CanonicalTreeBuilder.create(odb, child);
            createFeatureTypesTree(rootBuilder, "tree2", child);
            childrenFeatureTypesTree = rootBuilder.build();
            odb.put(childrenFeatureTypesTree);
        }

        RevTreeBuilder rootBuilder = CanonicalTreeBuilder.create(odb, childrenFeatureTypesTree);
        childTree1.put(featureRef("tree1", 1000));
        createFeatureTypesTree(rootBuilder, "tree1", childTree1.build());
        RevTree newRoot = rootBuilder.build();
        odb.put(newRoot);

        assertEquals(1, count(childrenFeatureTypesTree, newRoot).featureCount());

        childTree2.remove(featureRef("tree2", 2));
        rootBuilder = CanonicalTreeBuilder.create(odb, newRoot);
        RevTree child2 = childTree2.build();
        childTree2 = CanonicalTreeBuilder.create(odb, child2);
        createFeatureTypesTree(rootBuilder, "tree2", child2);
        newRoot = rootBuilder.build();
        rootBuilder = CanonicalTreeBuilder.create(odb, newRoot);
        odb.put(newRoot);
        assertEquals(2, count(childrenFeatureTypesTree, newRoot).featureCount());

        childTree2.put(RevObjectFactory.defaultInstance().createNode("tree2/1",
                FAKE_FEATURE_ID_CHANGED, ObjectId.NULL, TYPE.FEATURE, null, null));
        createFeatureTypesTree(rootBuilder, "tree2", childTree2.build());
        newRoot = rootBuilder.build();
        odb.put(newRoot);
        assertEquals(3, count(childrenFeatureTypesTree, newRoot).featureCount());
    }

    @Test
    public void testBucketBucketAdd() {
        RevTreeBuilder builder = CanonicalTreeBuilder.create(odb, bucketsFeatureTree);

        final int initialSize = (int) bucketsFeatureTree.size();
        final int added = 1 + 2 * CanonicalNodeNameOrder.normalizedSizeLimit(0);
        for (int i = initialSize; i < (initialSize + added); i++) {
            builder.put(featureRef("", i));
        }

        RevTree changed = builder.build();
        odb.put(changed);
        assertEquals(initialSize + added, changed.size());

        assertEquals(added, count(bucketsFeatureTree, changed).featureCount());
        assertEquals(added, count(changed, bucketsFeatureTree).featureCount());

        assertEquals(added, count(bucketsFeatureTree, changed).getFeaturesAdded());
        assertEquals(0, count(bucketsFeatureTree, changed).getFeaturesChanged());
        assertEquals(0, count(bucketsFeatureTree, changed).getFeaturesRemoved());

        // invert the comparison
        assertEquals(0, count(changed, bucketsFeatureTree).getFeaturesAdded());
        assertEquals(added, count(changed, bucketsFeatureTree).getFeaturesRemoved());
        assertEquals(0, count(changed, bucketsFeatureTree).getFeaturesChanged());
    }

    @Test
    public void testBucketBucketRemove() {
        CanonicalTreeBuilder builder = CanonicalTreeBuilder.create(odb, bucketsFeatureTree);

        RevTree changed;
        changed = builder.remove("3").build();
        odb.put(changed);
        assertEquals(1, count(bucketsFeatureTree, changed).featureCount());
        assertEquals(1, count(changed, bucketsFeatureTree).featureCount());

        builder = CanonicalTreeBuilder.create(odb, changed);
        for (int i = 0; i < CanonicalNodeNameOrder.normalizedSizeLimit(0) - 1; i++) {
            builder.remove(String.valueOf(i));
        }
        changed = builder.build();
        odb.put(changed);
        assertEquals(CanonicalNodeNameOrder.normalizedSizeLimit(0) + 1, changed.size());
        assertNotEquals(0, changed.bucketsSize());

        assertEquals(CanonicalNodeNameOrder.normalizedSizeLimit(0) - 1,
                count(bucketsFeatureTree, changed).featureCount());
        assertEquals(CanonicalNodeNameOrder.normalizedSizeLimit(0) - 1,
                count(changed, bucketsFeatureTree).featureCount());

        builder = CanonicalTreeBuilder.create(odb, changed);
        builder.remove(String.valueOf(CanonicalNodeNameOrder.normalizedSizeLimit(0) + 1));

        changed = builder.build();
        odb.put(changed);
        assertEquals(CanonicalNodeNameOrder.normalizedSizeLimit(0), changed.size());
        assertEquals(0, changed.bucketsSize());
    }

    @Test
    public void testBucketBucketChange() {
        RevTreeBuilder builder;
        RevTree changed;

        builder = CanonicalTreeBuilder.create(odb, bucketsFeatureTree);

        builder.put(RevObjectFactory.defaultInstance().createNode("1023", FAKE_FEATURE_ID_CHANGED,
                ObjectId.NULL, TYPE.FEATURE, null, null));
        changed = builder.build();
        odb.put(changed);

        DiffObjectCount count = count(bucketsFeatureTree, changed);
        assertEquals(1, count.featureCount());
        assertEquals(0, count.treeCount());
        count = count(changed, bucketsFeatureTree);
        assertEquals(1, count.featureCount());
        assertEquals(0, count.treeCount());

        builder = CanonicalTreeBuilder.create(odb, bucketsFeatureTree);
        int expected = 0;
        for (int i = 0; i < bucketsFeatureTree.size(); i += 2) {
            builder.put(RevObjectFactory.defaultInstance().createNode(String.valueOf(i),
                    FAKE_FEATURE_ID_CHANGED, ObjectId.NULL, TYPE.FEATURE, null, null));
            expected++;
        }
        changed = builder.build();
        odb.put(changed);
        assertEquals(expected, count(bucketsFeatureTree, changed).featureCount());
        assertEquals(expected, count(changed, bucketsFeatureTree).featureCount());

        assertEquals(expected, count(bucketsFeatureTree, changed).getFeaturesChanged());
        assertEquals(expected, count(changed, bucketsFeatureTree).getFeaturesChanged());
        assertEquals(0, count(changed, bucketsFeatureTree).getFeaturesAdded());
        assertEquals(0, count(changed, bucketsFeatureTree).getFeaturesRemoved());
    }

    @Test
    public void testBucketChildren() {
        CanonicalTreeBuilder builder = CanonicalTreeBuilder.create(odb, bucketsFeatureTree);
        RevTree changed;
        int normalizedSizeLimit = CanonicalNodeNameOrder.normalizedSizeLimit(0);
        for (int i = 0; i < normalizedSizeLimit; i++) {
            builder.remove(String.valueOf(i));
        }
        changed = builder.build();
        odb.put(changed);
        assertEquals(normalizedSizeLimit, changed.size());
        assertEquals(0, changed.bucketsSize());

        assertEquals(normalizedSizeLimit, count(bucketsFeatureTree, changed).getFeaturesRemoved());
        assertEquals(normalizedSizeLimit, count(bucketsFeatureTree, changed).featureCount());
        assertEquals(normalizedSizeLimit, count(changed, bucketsFeatureTree).featureCount());
    }

    @Test
    public void testBucketChildrenDeeperBuckets() {

        final RevTree deepTree = createFeaturesTree("",
                20000 + CanonicalNodeNameOrder.normalizedSizeLimit(0)).build();
        odb.put(deepTree);
        // sanity check
        assertNotEquals(0, deepTree.bucketsSize());

        {// sanity check to ensure we're testing with a tree with depth > 1 (i.e. at least two
         // levels of buckets)
            final int maxDepth = depth(deepTree, 0);
            assertTrue(maxDepth > 1);
        }

        CanonicalTreeBuilder builder = CanonicalTreeBuilder.create(odb, deepTree);
        {
            final int count = (int) (deepTree.size()
                    - CanonicalNodeNameOrder.normalizedSizeLimit(0));
            for (int i = 0; i < count; i++) {
                String path = String.valueOf(i);
                builder.remove(path);
            }
        }
        RevTree changed = builder.build();
        odb.put(changed);

        assertEquals(CanonicalNodeNameOrder.normalizedSizeLimit(0), changed.size());
        // sanity check
        assertFalse(changed.features().isEmpty());
        assertEquals(0, changed.bucketsSize());

        final long expected = deepTree.size() - changed.size();

        assertEquals(expected, count(deepTree, changed).featureCount());
        assertEquals(expected, count(changed, deepTree).featureCount());
    }

    private int depth(RevTree deepTree, int currDepth) {
        if (deepTree.bucketsSize() == 0) {
            return currDepth;
        }
        int depth = currDepth;
        for (Bucket bucket : deepTree.getBuckets()) {
            RevTree bucketTree = odb.get(bucket.getObjectId(), RevTree.class);
            int d = depth(bucketTree, currDepth + 1);
            depth = Math.max(depth, d);
        }
        return depth;
    }

    private RevTreeBuilder createFeaturesTree(final String parentPath, final int numEntries) {

        RevTreeBuilder tree = RevTreeBuilder.builder(odb);
        for (int i = 0; i < numEntries; i++) {
            tree.put(featureRef(parentPath, i));
        }
        return tree;
    }

    private Node featureRef(String parentPath, int i) {
        return RevObjectTestSupport.featureNode(parentPath, i);
    }

}
