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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.repository.DiffObjectCount;
import org.locationtech.geogig.storage.ObjectStore;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Timeout;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@State(Scope.Benchmark)
@BenchmarkMode(value = { Mode.AverageTime })
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgs = { "-Xms2G", "-Xmx2G" })
@Warmup(iterations = 1)
@Timeout(time = 2, timeUnit = TimeUnit.MINUTES)
@Measurement(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
public class PreOrderDiffWalkBenchmark {

    int numGetAllCalls;

    int numGetSingleTreeCalls;

    int numGetAllTreesFetched;

    int numUniqueTreesFetched;

    DiffObjectCount diffCount = null;

    @State(Scope.Thread)
    public static class ThreadState {

    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()//
                .include(PreOrderDiffWalkBenchmark.class.getSimpleName())//
                // .warmupIterations(5)//
                // .measurementIterations(5)//
                .forks(1)//
                .build();

        new Runner(opt).run();
    }

    @Fork(0)
    @BenchmarkMode(value = { Mode.SingleShotTime })
    @Warmup(iterations = 0)
    @Measurement(iterations = 1, batchSize = 1)
    public @Benchmark void ColdRun(Blackhole bh, PreOrderDiffWalkBenchmarkState state) {
        diffCount = testPreorderDiffWalkPerf(state);
        bh.consume(diffCount);

        numGetAllCalls = state.getGetAllCalls();
        numGetSingleTreeCalls = state.getGetSingleTreeCalls();
        numGetAllTreesFetched = state.getGetAllTreesFetched();
        numUniqueTreesFetched = state.getGetAllUniqueTreesCount();

        System.err.println("\n-----------------");
        System.err.printf(
                "#getTree calls:\t%d\t#getAll calls:\t%d\t#getAll trees fetched:\t%d\t#getAll unique trees:\t%d%n",
                numGetSingleTreeCalls, numGetAllCalls, numGetAllTreesFetched,
                numUniqueTreesFetched);
        System.err.println(diffCount);
        System.err.println("-----------------");
    }

    public @Benchmark void Concurrency_1(Blackhole bh, PreOrderDiffWalkBenchmarkState state)
            throws InterruptedException, ExecutionException {
        bh.consume(testPreorderDiffWalkPerf(state));
    }

    @Threads(2)
    public @Benchmark void Concurrency_2(Blackhole bh, PreOrderDiffWalkBenchmarkState state) {
        bh.consume(testPreorderDiffWalkPerf(state));
    }

    @Threads(8)
    public @Benchmark void Concurrency_8(Blackhole bh, PreOrderDiffWalkBenchmarkState state) {
        bh.consume(testPreorderDiffWalkPerf(state));
    }

    @Threads(16)
    public @Benchmark void Concurrency_16(Blackhole bh, PreOrderDiffWalkBenchmarkState state) {
        bh.consume(testPreorderDiffWalkPerf(state));
    }

    private static class AcceptAllDiffCountConsumer extends DiffCountConsumer {
        public AcceptAllDiffCountConsumer(ObjectStore leftSource, ObjectStore rightSource) {
            super(leftSource, rightSource);
        }

        // override to continue traversal down to the feature nodes instead of skipping whole
        // branches if possible
        public @Override boolean tree(NodeRef left, NodeRef right) {
            return true;
        }

        // override to continue traversal down to the feature nodes instead of skipping whole
        // branches if possible
        public @Override boolean bucket(NodeRef leftParent, NodeRef rightParent,
                BucketIndex bucketIndex, @Nullable Bucket left, @Nullable Bucket right) {
            return true;
        }
    }

    private DiffObjectCount testPreorderDiffWalkPerf(PreOrderDiffWalkBenchmarkState state) {

        AcceptAllDiffCountConsumer diffCountConsumer = new AcceptAllDiffCountConsumer(
                state.leftSourceWithLatency, state.rightSourceWithLatency);

        PreOrderDiffWalk visitor = new PreOrderDiffWalk(//
                state.left, //
                state.right, //
                state.leftSourceWithLatency, //
                state.rightSourceWithLatency, //
                state.forkJoinConcurrency);

        visitor.walk(diffCountConsumer);
        return diffCountConsumer.get();
    }

}
