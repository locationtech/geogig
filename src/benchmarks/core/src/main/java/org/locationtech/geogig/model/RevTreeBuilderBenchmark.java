/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Reports the performance of building large {@link RevTree}
 * <p>
 * The test is only run if the System property {@code geogig.runPerformanceTests} is set to
 * {@code true}
 * <p>
 * It also needs to be run with a rather high Heap size (4GB recommended)
 */
@State(Scope.Benchmark)
@BenchmarkMode(value = { Mode.SingleShotTime })
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgs = { "-Xms2G", "-Xmx12G" })
@Warmup(iterations = 1)
@Measurement(iterations = 2)
public class RevTreeBuilderBenchmark {

    public @Benchmark void putNodes(Blackhole bh, RevTreeBuilderBenchmarkState state) {
        Stream<Node> nodes = state.nodes();
        RevTreeBuilder builder = state.put(nodes);
        bh.consume(builder);
        builder.dispose();
    }

    public @Benchmark void buildTree(Blackhole bh, RevTreeBuilderBenchmarkState state) {
        RevTree tree = state.build(state.nodes());
        bh.consume(tree);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()//
                .include(RevTreeBuilderBenchmark.class.getSimpleName())//
                .warmupIterations(0)//
                .measurementIterations(5)//
                .forks(0)//
                .param("factory", "default")//
                .param("size", "10000000")//
                .param("storeType", "heap")//
                .build();

        new Runner(opt).run();
    }
}
