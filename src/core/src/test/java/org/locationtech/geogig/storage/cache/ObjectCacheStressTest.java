/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.cache;

import static org.locationtech.geogig.model.impl.RevObjectTestSupport.featureForceId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.RevObjectSerializerLZ4;
import org.locationtech.geogig.storage.datastream.v2_3.DataStreamRevObjectSerializerV2_3;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class ObjectCacheStressTest {

    final int featureCount = 1_000;

    final int treeCount = 1_000;

    private ObjectCache cache;

    List<RevFeature> features;// = createFeatures(ids);

    private List<RevTree> leafTrees;

    private List<RevTree> bucketTrees;

    private static final List<RevObjectSerializer> encoders = ImmutableList.of(//
            // raw encoders
            // DataStreamSerializationFactoryV1.INSTANCE //
            // , DataStreamSerializationFactoryV2.INSTANCE //
            // DataStreamSerializationFactoryV2_1.INSTANCE//
            // , DataStreamSerializationFactoryV2_3.INSTANCE//
            // LZF encoders
            // , new LZFSerializationFactory(DataStreamSerializationFactoryV1.INSTANCE)//
            // , new LZFSerializationFactory(DataStreamSerializationFactoryV2.INSTANCE)//
            // , new LZFSerializationFactory(DataStreamSerializationFactoryV2_1.INSTANCE)//
            // , new LZFSerializationFactory(DataStreamSerializationFactoryV2_3.INSTANCE)//
            // LZ4 encoders
            // , new LZ4SerializationFactory(DataStreamSerializationFactoryV1.INSTANCE)//
            // , new LZ4SerializationFactory(DataStreamSerializationFactoryV2.INSTANCE)//
            // , new LZ4SerializationFactory(DataStreamSerializationFactoryV2_1.INSTANCE)//
            new RevObjectSerializerLZ4(DataStreamRevObjectSerializerV2_3.INSTANCE)//
    );

    public static void main(String[] args) {
        ObjectCacheStressTest test = new ObjectCacheStressTest();
        System.err.println("set up...");
        try {
            test.setUp();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }

        final int runCount = 2;

        System.err.println("Leaf Trees test:");
        for (int i = 1; i <= runCount; i++) {
            test.runTest(test.leafTrees);
        }
        System.err.println(test.cache);
        test.tearDown();

        // System.err.println("Bucket Trees test:");
        // for (int i = 1; i <= runCount; i++) {
        // test.runTest(test.bucketTrees);
        // }
        // System.err.println(test.cache);
        // test.tearDown();
        //
        System.err.println("Features test:");
        for (int i = 1; i <= runCount; i++) {
            test.runTest(test.features);
        }
        System.err.println(test.cache);
        test.tearDown();
    }

    public void setUp() throws Exception {
        cache = CacheManager.INSTANCE.acquire("unique-cache-id");
        Stopwatch sw = Stopwatch.createStarted();
        Runtime runtime = Runtime.getRuntime();
        System.gc();
        System.runFinalization();
        Thread.sleep(3000);
        long mem = runtime.totalMemory() - Runtime.getRuntime().freeMemory();
        leafTrees = createLeafTrees(treeCount);
        System.gc();
        System.runFinalization();
        Thread.sleep(3000);
        long mem2 = runtime.totalMemory() - Runtime.getRuntime().freeMemory();
        System.err.printf("leaf tree mem: %,d\n", mem2 - mem);
        bucketTrees = createBucketTrees(treeCount);
        features = createFeatures(featureCount);
        sw.stop();
        System.err.printf("Created %,d features, %,d trees, %,d buckets in %s\n", featureCount,
                treeCount, treeCount, sw);
    }

    public void tearDown() {
        CacheManager.INSTANCE.release(cache);
        // Preconditions.checkState(cache.sizeBytes() == 0);
    }

    public void runTest(List<? extends RevObject> objects) {
        System.err.println(
                "----------------------------------------------------------------------------------");
        System.err.printf("Format\t\t Count\t Hits\t Insert\t\t Query\t\t Size\t\t Stats\n");
        for (RevObjectSerializer encoder : encoders) {
            cache.invalidateAll();
            run(encoder, objects);
        }
    }

    public void run(RevObjectSerializer encoder, List<? extends RevObject> objects) {
        // cache.setEncoder(encoder);
        final Stopwatch put = put(objects);
        try {
            Thread.currentThread().sleep(5000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        Collections.shuffle(objects);
        final Stopwatch get = Stopwatch.createStarted();
        int hits = query(Lists.transform(objects, (f) -> f.getId()));
        get.stop();
        System.err.printf("%s\t %,d\t %,d\t %s\t %s\t %,d\t %s\n", encoder.getDisplayName(),
                objects.size(), hits, put, get, CacheManager.INSTANCE.getSizeBytes(),
                ""/* cache.toString() */);
    }

    private int query(List<ObjectId> ids) {
        int hits = 0;
        for (ObjectId id : ids) {
            RevObject object = cache.getIfPresent(id);
            if (object != null) {
                hits++;
            }
        }
        return hits;
    }

    private Stopwatch put(List<? extends RevObject> objects) {
        Stopwatch sw = Stopwatch.createStarted();
        for (RevObject f : objects) {
            cache.put(f);
        }
        return sw.stop();
    }

    private List<RevTree> createLeafTrees(int count) {
        List<RevTree> trees = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RevTree tree = createLeafTree(i);
            trees.add(tree);
        }
        return trees;
    }

    private List<RevTree> createBucketTrees(int count) {
        List<RevTree> trees = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RevTree tree = createBucketTree(i);
            trees.add(tree);
        }
        return trees;
    }

    private RevTree createBucketTree(int i) {
        final int bucketCount = 32;
        SortedSet<Bucket> buckets = new TreeSet<>();
        for (int b = 0; b < bucketCount; b++) {
            ObjectId bucketTree = RevObjectTestSupport.hashString("b" + b);
            Envelope bounds = new Envelope(0, b, 0, b);
            Bucket bucket = RevObjectFactory.defaultInstance().createBucket(bucketTree, 0, bounds);
            buckets.add(bucket);
        }
        final ObjectId fakeId = RevObjectTestSupport.hashString(String.valueOf(i));
        RevTree tree = RevObjectFactory.defaultInstance().createTree(fakeId, 1024, 0, buckets);
        return tree;
    }

    private RevTree createLeafTree(int i) {
        final int numNodes = 512;
        List<Node> nodes = new ArrayList<>(numNodes);
        for (int n = 0; n < numNodes; n++) {
            Node node = createNodeWithMetadata(n);
            nodes.add(node);
        }

        ObjectId id = RevObjectTestSupport.hashString("fake-tree-" + i);
        RevTree tree = RevObjectFactory.defaultInstance().createTree(id, numNodes,
                Collections.emptyList(), ImmutableList.copyOf(nodes));
        return tree;
    }

    private Node createNodeWithMetadata(int n) {
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("some-root-key", "some-string-value-" + n);
        Map<String, Object> extraAtts = new HashMap<>();
        extraData.put(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA, extraAtts);
        extraAtts.put("string-key", "String-value-" + n);
        extraAtts.put("integer-key", n);
        extraAtts.put("a-very-large-key-name-just-for-testing-result-size", "large-key-value-" + n);

        String name = "Node-" + n;
        ObjectId oid = RevObjectTestSupport.hashString(name);
        Envelope bounds = new Envelope(-1 * n, n, -1 * n, n);
        return RevObjectFactory.defaultInstance().createNode(name, oid, ObjectId.NULL, TYPE.FEATURE,
                bounds, null);
    }

    private List<RevFeature> createFeatures(int count) {
        List<RevFeature> features = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ObjectId id = fakeId(i);
            features.add(fakeFeature(id));
        }
        return features;
    }

    private Geometry fakeGeom;

    private RevFeature fakeFeature(ObjectId forcedId) {
        // String oidString = objectId.toString();
        // ObjectId treeId = ObjectId.forString("tree" + oidString);
        // ImmutableList<ObjectId> parentIds = ImmutableList.of();
        // RevPerson author = new RevPersonImpl("Gabriel", "groldan@boundlessgeo.com", 1000, -3);
        // RevPerson committer = new RevPersonImpl("Gabriel", "groldan@boundlessgeo.com", 1000, -3);
        // String message = "message " + oidString;
        // return new RevCommitImpl(objectId, treeId, parentIds, author, committer, message);

        if (fakeGeom == null) {
            try {
                fakeGeom = new WKTReader().read(
                        "MULTIPOLYGON (((-121.3647138 38.049474, -121.3646902 38.049614, -121.3646159 38.0496058, -121.3646188 38.049587, -121.3645936 38.049586, -121.3645924 38.0496222, -121.3645056 38.0496178, -121.3645321 38.0494567, -121.3647138 38.049474)))");
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        return featureForceId(forcedId, fakeGeom, "Some string value " + forcedId);
    }

    private ObjectId fakeId(int i) {
        return RevObjectTestSupport.hashString("fakeID" + i);
    }
}
