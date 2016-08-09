/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.CanonicalNodeOrder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObjectTestSupport;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.vividsolutions.jts.geom.Envelope;

public abstract class RevTreeSerializationTest extends Assert {
    protected ObjectSerializingFactory serializer = getObjectSerializingFactory();

    protected abstract ObjectSerializingFactory getObjectSerializingFactory();

    private RevTree tree1_leaves;

    private RevTree tree2_internal;

    private RevTree tree3_buckets;

    private RevTree tree4_spatial_leaves;

    private RevTree tree5_spatial_internal;

    private RevTree tree6_spatial_buckets;

    @Before
    public void initialize() {
        ImmutableList<Node> features = ImmutableList
                .of(Node.create("foo", RevObjectTestSupport.hashString("nodeid"),
                        RevObjectTestSupport.hashString("metadataid"), RevObject.TYPE.FEATURE, null));
        ImmutableList<Node> spatialFeatures = ImmutableList.of(Node.create("foo",
                RevObjectTestSupport.hashString("nodeid"), RevObjectTestSupport.hashString("metadataid"),
                RevObject.TYPE.FEATURE, new Envelope(0.0000001, 0.0000002, 0.0000001, 0.0000002)));
        ImmutableList<Node> trees = ImmutableList
                .of(Node.create("bar", RevObjectTestSupport.hashString("barnodeid"),
                        RevObjectTestSupport.hashString("barmetadataid"), RevObject.TYPE.TREE, null));
        ImmutableList<Node> spatialTrees = ImmutableList.of(Node.create("bar",
                RevObjectTestSupport.hashString("barnodeid"), RevObjectTestSupport.hashString("barmetadataid"),
                RevObject.TYPE.TREE, new Envelope(1, 2, 1, 2)));

        SortedMap<Integer, Bucket> spatialBuckets = ImmutableSortedMap.of(1,
                Bucket.create(RevObjectTestSupport.hashString("buckettree"), new Envelope()));

        SortedMap<Integer, Bucket> buckets = ImmutableSortedMap.of(1,
                Bucket.create(RevObjectTestSupport.hashString("buckettree"), new Envelope(1, 2, 1, 2)));

        tree1_leaves = RevTreeBuilder.create(RevObjectTestSupport.hashString("leaves"), 1L, 0, null, features,
                null);
        tree2_internal = RevTreeBuilder.create(RevObjectTestSupport.hashString("internal"), 0, trees.size(),
                trees, null, null);
        tree3_buckets = RevTreeBuilder.create(RevObjectTestSupport.hashString("buckets"), 1L, 1, null, null,
                buckets);
        tree4_spatial_leaves = RevTreeBuilder.create(RevObjectTestSupport.hashString("leaves"), 1L, 0, null,
                spatialFeatures, null);
        tree5_spatial_internal = RevTreeBuilder.create(RevObjectTestSupport.hashString("internal"), 1L,
                spatialTrees.size(), spatialTrees, null, null);
        tree6_spatial_buckets = RevTreeBuilder.create(RevObjectTestSupport.hashString("buckets"), 1L, 1, null,
                null, spatialBuckets);
    }

    @Test
    public void testRoundTripLeafTree() throws IOException {
        RevTree roundTripped = read(tree1_leaves.getId(), write(tree1_leaves));
        assertTreesAreEqual(tree1_leaves, roundTripped);
    }

    @Test
    public void testRoundTripInternalTree() throws IOException {
        RevTree roundTripped = read(tree2_internal.getId(), write(tree2_internal));
        assertTreesAreEqual(tree2_internal, roundTripped);
    }

    @Test
    public void testRoundTripBuckets() throws IOException {
        RevTree roundTripped = read(tree3_buckets.getId(), write(tree3_buckets));
        assertTreesAreEqual(tree3_buckets, roundTripped);
    }

    @Test
    public void testRoundTripBucketsFull() throws IOException {

        ObjectId id = RevObjectTestSupport.hashString("fake");
        long size = 100000000;
        int childTreeCount = 0;
        SortedMap<Integer, Bucket> bucketTrees = createBuckets(32);

        final RevTree tree = RevTreeBuilder.create(id, size, childTreeCount, null, null,
                bucketTrees);

        RevTree roundTripped = read(tree.getId(), write(tree));
        assertTreesAreEqual(tree, roundTripped);

    }

    private SortedMap<Integer, Bucket> createBuckets(int count) {
        SortedMap<Integer, Bucket> buckets = new TreeMap<>();
        for (int i = 0; i < count; i++) {
            Bucket bucket = Bucket.create(RevObjectTestSupport.hashString("b" + i),
                    new Envelope(i, i * 2, i, i * 2));
            buckets.put(i, bucket);
        }
        return buckets;
    }

    @Test
    public void testRoundTripSpatialLeafTree() throws IOException {
        RevTree roundTripped = read(tree4_spatial_leaves.getId(), write(tree4_spatial_leaves));
        assertTreesAreEqual(tree4_spatial_leaves, roundTripped);
    }

    @Test
    public void testRoundTripSpatialInternalTree() throws IOException {
        RevTree roundTripped = read(tree5_spatial_internal.getId(), write(tree5_spatial_internal));
        assertTreesAreEqual(tree5_spatial_internal, roundTripped);
    }

    @Test
    public void testRoundTripSpatialBuckets() throws IOException {
        RevTree roundTripped = read(tree6_spatial_buckets.getId(), write(tree6_spatial_buckets));
        assertTreesAreEqual(tree6_spatial_buckets, roundTripped);
    }

    private byte[] write(RevTree tree) {
        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            serializer.write(tree, bout);
            return bout.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private RevTree read(ObjectId id, byte[] bytes) throws IOException {
        ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
        return (RevTree) serializer.read(id, bin);
    }

    private void assertTreesAreEqual(RevTree a, RevTree b) {
        assertTrue(a.getId().equals(b.getId()));
        assertTrue(a.buckets().equals(b.buckets()));
        assertTrue(a.features().equals(b.features()));
        assertTrue(a.trees().equals(b.trees()));
        assertTrue(a.numTrees() == b.numTrees());
        assertTrue(a.size() == b.size());

        Iterator<? extends Bounded> ia;
        Iterator<? extends Bounded> ib;
        if (a.buckets().isEmpty()) {
            ia = RevObjects.children(a, CanonicalNodeOrder.INSTANCE);
            ib = RevObjects.children(b, CanonicalNodeOrder.INSTANCE);
        } else {
            ia = a.buckets().values().iterator();
            ib = b.buckets().values().iterator();
        }

        // bounds are not part of the Bounded.equals(Object) contract as its auxiliary information
        while (ia.hasNext()) {
            Bounded ba = ia.next();
            Bounded bb = ib.next();
            Envelope ea = new Envelope();
            Envelope eb = new Envelope();
            ba.expand(ea);
            bb.expand(eb);
            assertEquals(ea.getMinX(), eb.getMinX(), 1e-7D);
            assertEquals(ea.getMinY(), eb.getMinY(), 1e-7D);
            assertEquals(ea.getMaxX(), eb.getMaxX(), 1e-7D);
            assertEquals(ea.getMaxY(), eb.getMaxY(), 1e-7D);
        }
    }

}
