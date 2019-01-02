/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cache.performance;

import static org.locationtech.geogig.model.impl.RevObjectTestSupport.featureForceId;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.geotools.io.TableWriter;
import org.locationtech.geogig.cache.caffeine.CaffeineSharedCache;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectFactoryImpl;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.cache.CacheIdentifier;
import org.locationtech.geogig.storage.cache.ObjectCache;
import org.locationtech.geogig.storage.cache.SharedCache;
import org.locationtech.geogig.storage.datastream.v2_3.DataStreamRevObjectSerializerV2_3;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;

import lombok.Value;

public class ObjectCacheStressTest {

//     private final RevObjectFactory objectFactory = new FlatBuffersRevObjectFactory();
    private final RevObjectFactory objectFactory = new RevObjectFactoryImpl();

    final int numPutAndGetThreads = 16;

    final ForkJoinPool forkJoinPool = new ForkJoinPool(numPutAndGetThreads);

    final int featureCount = 100_000;

    final int treeCount = 100_000;

    final int bucketTreeCount = 100_000;

    private ObjectCache cache;

    List<RevFeature> features;// = createFeatures(ids);

    private List<RevTree> leafTrees;

    private List<RevTree> bucketTrees;

    private SharedCache sharedCache;

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
            // new LZFSerializationFactory(DataStreamSerializationFactoryV2_3.INSTANCE)//
            // LZ4 encoders
            // , new LZ4SerializationFactory(DataStreamSerializationFactoryV1.INSTANCE)//
            // , new LZ4SerializationFactory(DataStreamSerializationFactoryV2.INSTANCE)//
            // , new LZ4SerializationFactory(DataStreamSerializationFactoryV2_1.INSTANCE)//
            // new LZ4SerializationFactory(DataStreamSerializationFactoryV2_3.INSTANCE)//
            // DataStreamSerializationFactoryV2_2.INSTANCE//
             DataStreamRevObjectSerializerV2_3.INSTANCE//
//            new FlatBuffersRevObjectSerializer()//
    );

    public static void main(String[] args) {
        ObjectCacheStressTest test = new ObjectCacheStressTest();
        int L1Capacity = 0;// test.treeCount;
        long maxSizeBytes = 32L * 1024 * 1024 * 1024;
        SharedCache sharedCache;
        sharedCache = new CaffeineSharedCache(L1Capacity, maxSizeBytes);
        // sharedCache = new GuavaSharedCache(L1Capacity, maxSizeBytes);
        System.err.println("set up: object factory: " + test.objectFactory.getClass().getName());
        try {
            test.setUp(() -> sharedCache);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
        final String cacheImplName = sharedCache.getClass().getSimpleName();
        final int runCount = 10;

        if (test.treeCount > 0) {
            for (int i = 1; i <= runCount; i++) {
                List<TestResult> leafTreesResults = test.runTest(test.leafTrees);
                printResults(String.format("Leaf Trees run %d/%d", i, runCount), cacheImplName,
                        test.numPutAndGetThreads, leafTreesResults);
            }
            test.tearDown();
        }
        if (test.bucketTreeCount > 0) {
            for (int i = 1; i <= runCount; i++) {
                List<TestResult> bucketTreesResults = test.runTest(test.bucketTrees);
                printResults(String.format("Bucket Trees run %d/%d", i, runCount), cacheImplName,
                        test.numPutAndGetThreads, bucketTreesResults);
            }
            test.tearDown();
        }
        if (test.featureCount > 0) {
            for (int i = 1; i <= runCount; i++) {
                List<TestResult> results = test.runTest(test.features);
                printResults(String.format("Features run %d/%d", i, runCount), cacheImplName,
                        test.numPutAndGetThreads, results);
            }
            test.tearDown();
        }
    }

    private static void printResults(String testName, String cacheImpl, int putAndGetThreads,
            List<TestResult> results) {

        TableWriter w = new TableWriter(new OutputStreamWriter(System.err, Charsets.UTF_8));
        w.setMultiLinesCells(false);
        w.nextLine(TableWriter.DOUBLE_HORIZONTAL_LINE);
        writeColumn(w, testName, cacheImpl, String.format("Threads: %d", putAndGetThreads));
        w.nextLine(' ');
        w.nextLine(TableWriter.DOUBLE_HORIZONTAL_LINE);
        writeColumn(w, "Cache Format", "Object count", "Hits", "Insert time", "Query time", "Size");
        w.nextLine();
        w.writeHorizontalSeparator();
        results.forEach(i -> {
            writeColumn(w, formatValues(i));
            w.nextLine();
        });
        w.nextLine(TableWriter.DOUBLE_HORIZONTAL_LINE);
        try {
            w.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static List<String> formatValues(TestResult r) {
        String count = String.format("%,d", r.getCount());
        String hits = String.format("%,d", r.getHits());
        String insert = String.format("%,dms", r.getInsertTimeMillis());
        String query = String.format("%,dms", r.getQueryTimeMillis());
        String size = String.format("%,d", r.getSizeBytes());
        return Arrays.asList(r.getFormat(), count, hits, insert, query, size);
    }

    private static void writeColumn(TableWriter w, String... values) {
        writeColumn(w, Arrays.asList(values));
    }

    private static void writeColumn(TableWriter w, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            w.write(values.get(i));
            if (i < values.size() - 1) {
                w.nextColumn();
            }
        }
    }

    public void setUp(Supplier<SharedCache> sharedCache) throws Exception {
        this.sharedCache = sharedCache.get();
        cache = new ObjectCache(sharedCache, new CacheIdentifier(1));
        Stopwatch sw = Stopwatch.createStarted();
        Runtime runtime = Runtime.getRuntime();
        long mem = runtime.totalMemory() - Runtime.getRuntime().freeMemory();
        leafTrees = createLeafTrees(treeCount);
        long mem2 = runtime.totalMemory() - Runtime.getRuntime().freeMemory();
        System.err.printf("leaf tree mem: %,d\n", mem2 - mem);
        bucketTrees = createBucketTrees(bucketTreeCount);
        features = createFeatures(featureCount);
        sw.stop();
        System.err.printf("Created %,d features, %,d trees, %,d buckets in %s\n", featureCount,
                treeCount, treeCount, sw);
    }

    public void tearDown() {
        sharedCache.invalidateAll();
        // CacheManager.INSTANCE.release(cache);
        // Preconditions.checkState(cache.sizeBytes() == 0);
    }

    public List<TestResult> runTest(List<? extends RevObject> objects) {
        List<TestResult> results = new ArrayList<>();
        for (RevObjectSerializer encoder : encoders) {
            cache.invalidateAll();
            TestResult result = run(encoder, objects);
            results.add(result);
        }
        return results;
    }

    public static @Value class TestResult {
        private final String format;

        private final int count;

        private final int hits;

        private final long insertTimeMillis;

        private final long queryTimeMillis;

        private final long sizeBytes;
    }

    public TestResult run(RevObjectSerializer encoder, List<? extends RevObject> objects) {
        sharedCache.setEncoder(encoder);
        final Stopwatch put = put(objects);
        Collections.shuffle(objects);
        final Stopwatch get = Stopwatch.createStarted();
        int hits = query(objects);
        get.stop();

        long sizeBytes = sharedCache.sizeBytes();
        String displayName = encoder.getDisplayName();
        int size = objects.size();

        long insertTimeMillis = put.elapsed(TimeUnit.MILLISECONDS);
        long queryTimeMillis = get.elapsed(TimeUnit.MILLISECONDS);

        TestResult result = new TestResult(displayName, size, hits, insertTimeMillis,
                queryTimeMillis, sizeBytes);
        return result;
    }

    private int query(List<? extends RevObject> queryObjects) {
        AtomicInteger hits = new AtomicInteger();
        try {
            forkJoinPool.submit(() -> queryObjects.parallelStream().forEach(o -> {
                RevObject cached = cache.getIfPresent(o.getId());
                if (cached != null) {
                    hits.incrementAndGet();
                }
            })).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        return hits.intValue();
    }

    private Stopwatch put(List<? extends RevObject> objects) {
        Stopwatch sw = Stopwatch.createStarted();
        try {
            forkJoinPool.submit(() -> objects.parallelStream().forEach(cache::put)).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
        return sw.stop();
    }

    private List<RevTree> createLeafTrees(final int count) {
        return IntStream.range(0, count).parallel().mapToObj(this::createLeafTree)
                .collect(Collectors.toList());
    }

    private List<RevTree> createBucketTrees(int count) {
        return IntStream.range(0, count).parallel().mapToObj(this::createBucketTree)
                .collect(Collectors.toList());
    }

    private RevTree createBucketTree(int i) {
        final int bucketCount = 32;
        SortedSet<Bucket> buckets = new TreeSet<>();
        for (int b = 0; b < bucketCount; b++) {
            ObjectId bucketTree = RevObjectTestSupport.hashString("b" + b);
            Envelope bounds = new Envelope(0, b, 0, b);
            Bucket bucket = objectFactory.createBucket(bucketTree, b, bounds);
            buckets.add(bucket);
        }
        final ObjectId fakeId = RevObjectTestSupport.hashString(String.valueOf(i));
        RevTree tree = objectFactory.createTree(fakeId, 1024, 0, buckets);
        return tree;
    }

    private RevTree createLeafTree(int i) {
        final int numNodes = 512;
        List<Node> nodes = IntStream.range(0, numNodes).mapToObj(this::createNode)
                .collect(Collectors.toList());
        ObjectId id = ObjectId.create(i, i * i, i * i * i);
        RevTree tree = objectFactory.createTree(id, numNodes, Collections.emptyList(),
                ImmutableList.copyOf(nodes));
        return tree;
    }

    private Node createNode(int n) {
        String name = "Node-" + n;
        ObjectId oid = RevObjectTestSupport.hashString(name);
        Envelope bounds = new Envelope(-1 * n, n, -1 * n, n);
        return objectFactory.createNode(name, oid, ObjectId.NULL, TYPE.FEATURE, bounds, null);
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
        return objectFactory.createNode(name, oid, ObjectId.NULL, TYPE.FEATURE, bounds, null);
    }

    private List<RevFeature> createFeatures(final int count) {
        return IntStream.range(0, count).parallel().mapToObj(this::fakeFeature)
                .collect(Collectors.toList());
    }

    private Geometry fakeGeom;

    private RevFeature fakeFeature(int i) {
        ObjectId forcedId = fakeId(i);
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
