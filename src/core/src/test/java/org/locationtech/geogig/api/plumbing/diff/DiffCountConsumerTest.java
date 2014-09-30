/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.diff;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectDatabse;

/**
 *
 */
public class DiffCountConsumerTest extends Assert {

    /**
     * All original feature noderefs have this objectid
     */
    private static final ObjectId FAKE_FEATURE_ID = ObjectId
            .forString("1100000000000000000000000000000000000000");

    /**
     * All changed feature noderefs have this objectid
     */
    private static final ObjectId FAKE_FEATURE_ID_CHANGED = ObjectId
            .forString("2200000000000000000000000000000000000000");

    private ObjectDatabase odb;

    private RevTree childrenFeatureTree;

    /** single level tree with 2 * {@link RevTree#NORMALIZED_SIZE_LIMIT} feature references */
    private RevTree bucketsFeatureTree;

    private RevTree childrenFeatureTypesTree;

    RevTreeBuilder childTree1;

    RevTreeBuilder childTree2;

    @Before
    public void setUp() {
        odb = new HeapObjectDatabse();
        odb.open();
        {
            RevTreeBuilder builder = createFeaturesTree("", 10);
            this.childrenFeatureTree = builder.build();
        }
        {
            RevTreeBuilder rootBuilder = new RevTreeBuilder(odb);
            childTree1 = createFeaturesTree("tree1", 10);
            createFeatureTypesTree(rootBuilder, "tree1", childTree1);
            childTree2 = createFeaturesTree("tree2", 5);
            createFeatureTypesTree(rootBuilder, "tree2", childTree2);
            childrenFeatureTypesTree = rootBuilder.build();
        }

        {
            RevTreeBuilder builder = createFeaturesTree("", 2 * RevTree.NORMALIZED_SIZE_LIMIT);
            this.bucketsFeatureTree = builder.build();
            assertTrue(bucketsFeatureTree.buckets().isPresent());
        }
    }

    private void createFeatureTypesTree(RevTreeBuilder rootBuilder, String treePath,
            RevTreeBuilder childBuilder) {
        RevTree childTree = childBuilder.build();
        odb.put(childTree);
        Node childRef = Node.create(treePath, childTree.getId(), ObjectId.NULL, TYPE.TREE, null);
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
        assertEquals(childrenFeatureTree.size(), count(childrenFeatureTree, RevTree.EMPTY)
                .featureCount());
        assertEquals(childrenFeatureTree.size(), count(RevTree.EMPTY, childrenFeatureTree)
                .featureCount());
    }

    @Test
    public void testChildrenChildren() {
        RevTreeBuilder builder = new RevTreeBuilder(odb, childrenFeatureTree);
        RevTree changed = builder.remove("3").build();
        assertEquals(1, count(childrenFeatureTree, changed).featureCount());
        assertEquals(1, count(changed, childrenFeatureTree).featureCount());

        changed = builder.put(
                Node.create("new", FAKE_FEATURE_ID, ObjectId.NULL, TYPE.FEATURE, null)).build();
        assertEquals(2, count(childrenFeatureTree, changed).featureCount());
        assertEquals(2, count(changed, childrenFeatureTree).featureCount());

        changed = builder.put(
                Node.create("1", FAKE_FEATURE_ID_CHANGED, ObjectId.NULL, TYPE.FEATURE, null))
                .build();
        assertEquals(3, count(childrenFeatureTree, changed).featureCount());
        assertEquals(3, count(changed, childrenFeatureTree).featureCount());
    }

    @Test
    public void testChildrenChildrenNestedTrees() {
        RevTreeBuilder rootBuilder = new RevTreeBuilder(odb, childrenFeatureTypesTree);
        childTree1.put(featureRef("tree1", 1000));
        createFeatureTypesTree(rootBuilder, "tree1", childTree1);
        RevTree newRoot = rootBuilder.build();

        assertEquals(1, count(childrenFeatureTypesTree, newRoot).featureCount());

        childTree2.remove("tree2/2");
        createFeatureTypesTree(rootBuilder, "tree2", childTree2);
        newRoot = rootBuilder.build();
        assertEquals(2, count(childrenFeatureTypesTree, newRoot).featureCount());

        childTree2.put(Node.create("tree2/1", FAKE_FEATURE_ID_CHANGED, ObjectId.NULL, TYPE.FEATURE,
                null));
        createFeatureTypesTree(rootBuilder, "tree2", childTree2);
        newRoot = rootBuilder.build();
        assertEquals(3, count(childrenFeatureTypesTree, newRoot).featureCount());
    }

    @Test
    public void testBucketBucketAdd() {
        RevTreeBuilder builder = new RevTreeBuilder(odb, bucketsFeatureTree);

        final int initialSize = (int) bucketsFeatureTree.size();
        final int added = 1 + 2 * RevTree.NORMALIZED_SIZE_LIMIT;
        for (int i = initialSize; i < (initialSize + added); i++) {
            builder.put(featureRef("", i));
        }

        RevTree changed = builder.build();
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
        RevTreeBuilder builder = new RevTreeBuilder(odb, bucketsFeatureTree);

        RevTree changed;
        changed = builder.remove("3").build();
        assertEquals(1, count(bucketsFeatureTree, changed).featureCount());
        assertEquals(1, count(changed, bucketsFeatureTree).featureCount());

        for (int i = 0; i < RevTree.NORMALIZED_SIZE_LIMIT - 1; i++) {
            builder.remove(String.valueOf(i));
        }
        changed = builder.build();
        assertEquals(RevTree.NORMALIZED_SIZE_LIMIT + 1, changed.size());
        assertTrue(changed.buckets().isPresent());

        assertEquals(RevTree.NORMALIZED_SIZE_LIMIT - 1, count(bucketsFeatureTree, changed)
                .featureCount());
        assertEquals(RevTree.NORMALIZED_SIZE_LIMIT - 1, count(changed, bucketsFeatureTree)
                .featureCount());

        builder.remove(String.valueOf(RevTree.NORMALIZED_SIZE_LIMIT + 1));

        changed = builder.build();
        assertEquals(RevTree.NORMALIZED_SIZE_LIMIT, changed.size());
        assertFalse(changed.buckets().isPresent());
    }

    @Test
    public void testBucketBucketChange() {
        RevTreeBuilder builder;
        RevTree changed;

        builder = new RevTreeBuilder(odb, bucketsFeatureTree);

        changed = builder.put(
                Node.create("1023", FAKE_FEATURE_ID_CHANGED, ObjectId.NULL, TYPE.FEATURE, null))
                .build();
        DiffObjectCount count = count(bucketsFeatureTree, changed);
        assertEquals(1, count.featureCount());
        assertEquals(0, count.treeCount());
        count = count(changed, bucketsFeatureTree);
        assertEquals(1, count.featureCount());
        assertEquals(0, count.treeCount());

        builder = new RevTreeBuilder(odb, bucketsFeatureTree);
        int expected = 0;
        for (int i = 0; i < bucketsFeatureTree.size(); i += 2) {
            changed = builder.put(
                    Node.create(String.valueOf(i), FAKE_FEATURE_ID_CHANGED, ObjectId.NULL,
                            TYPE.FEATURE, null)).build();
            expected++;
        }
        changed = builder.build();
        assertEquals(expected, count(bucketsFeatureTree, changed).featureCount());
        assertEquals(expected, count(changed, bucketsFeatureTree).featureCount());

        assertEquals(expected, count(bucketsFeatureTree, changed).getFeaturesChanged());
        assertEquals(expected, count(changed, bucketsFeatureTree).getFeaturesChanged());
        assertEquals(0, count(changed, bucketsFeatureTree).getFeaturesAdded());
        assertEquals(0, count(changed, bucketsFeatureTree).getFeaturesRemoved());
    }

    @Test
    public void testBucketChildren() {
        RevTreeBuilder builder = new RevTreeBuilder(odb, bucketsFeatureTree);
        RevTree changed;
        for (int i = 0; i < RevTree.NORMALIZED_SIZE_LIMIT; i++) {
            builder.remove(String.valueOf(i));
        }
        changed = builder.build();
        assertEquals(RevTree.NORMALIZED_SIZE_LIMIT, changed.size());
        assertFalse(changed.buckets().isPresent());

        assertEquals(RevTree.NORMALIZED_SIZE_LIMIT, count(bucketsFeatureTree, changed)
                .featureCount());
        assertEquals(RevTree.NORMALIZED_SIZE_LIMIT, count(changed, bucketsFeatureTree)
                .featureCount());
    }

    @Test
    public void testBucketChildrenDeeperBuckets() {

        final RevTree deepTree = createFeaturesTree("", 20000 + RevTree.NORMALIZED_SIZE_LIMIT)
                .build();
        // sanity check
        assertTrue(deepTree.buckets().isPresent());

        {// sanity check to ensure we're testing with a tree with depth > 1 (i.e. at least two
         // levels of buckets)
            final int maxDepth = depth(deepTree, 0);
            assertTrue(maxDepth > 1);
        }

        RevTreeBuilder builder = new RevTreeBuilder(odb, deepTree);
        {
            final int count = (int) (deepTree.size() - RevTree.NORMALIZED_SIZE_LIMIT);
            for (int i = 0; i < count; i++) {
                String path = String.valueOf(i);
                builder.remove(path);
            }
        }
        RevTree changed = builder.build();
        assertEquals(RevTree.NORMALIZED_SIZE_LIMIT, changed.size());
        // sanity check
        assertTrue(changed.features().isPresent());
        assertFalse(changed.buckets().isPresent());

        final long expected = deepTree.size() - changed.size();

        assertEquals(expected, count(deepTree, changed).featureCount());
        assertEquals(expected, count(changed, deepTree).featureCount());
    }

    private int depth(RevTree deepTree, int currDepth) {
        if (!deepTree.buckets().isPresent()) {
            return currDepth;
        }
        int depth = currDepth;
        for (Bucket bucket : deepTree.buckets().get().values()) {
            RevTree bucketTree = odb.get(bucket.id(), RevTree.class);
            int d = depth(bucketTree, currDepth + 1);
            depth = Math.max(depth, d);
        }
        return depth;
    }

    private RevTreeBuilder createFeaturesTree(final String parentPath, final int numEntries) {

        RevTreeBuilder tree = new RevTreeBuilder(odb);
        for (int i = 0; i < numEntries; i++) {
            tree.put(featureRef(parentPath, i));
        }
        return tree;
    }

    private Node featureRef(String parentPath, int i) {
        String path = NodeRef.appendChild(parentPath, String.valueOf(i));
        Node ref = Node.create(path, FAKE_FEATURE_ID, ObjectId.NULL, TYPE.FEATURE, null);
        return ref;
    }

}
