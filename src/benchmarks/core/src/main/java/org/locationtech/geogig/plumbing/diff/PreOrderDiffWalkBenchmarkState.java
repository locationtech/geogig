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

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.BulkOpListener.CountingListener;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.decorator.ForwardingObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectStore;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class PreOrderDiffWalkBenchmarkState {

    @Param(value = { "1", "2", "8", "32" })
    int forkJoinConcurrency;

    @Param(value = { "0" })
    int getAllLatency;

    @Param(value = { "0" })
    int getSingleLatency;

    @Param(value = { "512", "1000000" })
    int treeLeftSize;

    @Param(value = { "1000000" })
    int treeRightSize;

    @Param(value = { "true" })
    boolean sameStore;

    ObjectStore leftSource, rightSource;

    LatencyObjectStore leftSourceWithLatency, rightSourceWithLatency;

    RevTree left, right;

    public @Setup(Level.Trial) void prepareData() throws InterruptedException, ExecutionException {
        leftSource = new HeapObjectStore();
        leftSource.open();
        if (sameStore) {
            rightSource = leftSource;
        } else {
            rightSource = new HeapObjectStore();
            rightSource.open();
        }

        final boolean randomIds = false;
        CompletableFuture<RevTree> leftFuture;
        CompletableFuture<RevTree> rightFuture;
        {
            leftFuture = CompletableFuture.supplyAsync(() -> RevObjectTestSupport.INSTANCE
                    .createLargeFeaturesTree(leftSource, "f", treeLeftSize, 0, randomIds));

            int startIndex = Math.min(treeLeftSize, treeRightSize) / 2;
            rightFuture = CompletableFuture.supplyAsync(() -> RevObjectTestSupport.INSTANCE
                    .createLargeFeaturesTree(rightSource, "f", treeRightSize, startIndex, true));
        }
        left = leftFuture.get();
        right = rightFuture.get();
        System.err.println("Left tree : " + RevObjects.toShortString(left));
        System.err.println("Right tree: " + RevObjects.toShortString(right));
    }

    public @Setup(Level.Invocation) void setUpLatencyStores()
            throws InterruptedException, ExecutionException {
        leftSourceWithLatency = new LatencyObjectStore(leftSource, getAllLatency);
        if (sameStore) {
            rightSourceWithLatency = leftSourceWithLatency;
        } else {
            leftSourceWithLatency = new LatencyObjectStore(rightSource, getAllLatency);
        }
    }

    public @TearDown void tearDown() {
        leftSource.close();
        rightSource.close();
    }

    public int getGetAllCalls() {
        int numCalls = leftSourceWithLatency.getAllCalls.get();
        if (leftSourceWithLatency != rightSourceWithLatency) {
            numCalls += rightSourceWithLatency.getAllCalls.get();
        }
        return numCalls;
    }

    public int getGetAllTreesFetched() {
        int getAllFetched = leftSourceWithLatency.getAllCountingListener.found();
        if (leftSourceWithLatency != rightSourceWithLatency) {
            getAllFetched += rightSourceWithLatency.getAllCountingListener.found();
        }
        return getAllFetched;
    }

    public int getGetAllUniqueTreesCount() {
        int getAllFetched = leftSourceWithLatency.getAllCountingListener.uniqueIds.size();
        if (leftSourceWithLatency != rightSourceWithLatency) {
            getAllFetched += rightSourceWithLatency.getAllCountingListener.uniqueIds.size();
        }
        return getAllFetched;
    }

    public int getGetSingleTreeCalls() {
        int numCalls = leftSourceWithLatency.getSingleTreeCalls.get();
        if (leftSourceWithLatency != rightSourceWithLatency) {
            numCalls += rightSourceWithLatency.getSingleTreeCalls.get();
        }
        return numCalls;
    }

    private static class UniqueObjectsListener extends CountingListener {

        private Set<ObjectId> uniqueIds = ConcurrentHashMap.newKeySet();

        public @Override void found(ObjectId object, @Nullable Integer storageSizeBytes) {
            super.found(object, storageSizeBytes);
            uniqueIds.add(object);
        }

    }

    public static class LatencyObjectStore extends ForwardingObjectStore {

        private long ioLatencyMs;

        private long singleLatency;

        final AtomicInteger getAllCalls = new AtomicInteger();

        final AtomicInteger getSingleTreeCalls = new AtomicInteger();

        private UniqueObjectsListener getAllCountingListener = new UniqueObjectsListener();

        public LatencyObjectStore(ObjectStore odb, long ioLatencyMs) {
            super(odb);
            this.ioLatencyMs = ioLatencyMs;
            this.singleLatency = 0;// ioLatencyMs / 3;
        }

        public @Override <T extends RevObject> Iterator<T> getAll(Iterable<ObjectId> ids,
                BulkOpListener listener, Class<T> type) {
            getAllCalls.incrementAndGet();
            Iterator<T> it = simulateLatency(ioLatencyMs,
                    () -> super.getAll(ids, this.getAllCountingListener, type));
            return it;
        }

        public @Override RevTree getTree(ObjectId id) {
            getSingleTreeCalls.incrementAndGet();
            return simulateLatency(singleLatency, () -> super.getTree(id));
        }

        private <T> T simulateLatency(long latency, Supplier<T> supplier) {
            T res = supplier.get();
            if (latency > 0) {
                try {
                    Thread.sleep(latency);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            }
            return res;
        }
    }

}
