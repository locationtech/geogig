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

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.flatbuffers.FlatBuffersRevObjectFactory;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.model.impl.RevObjectFactoryImpl;
import org.locationtech.geogig.rocksdb.RocksdbObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.memory.HeapObjectStore;
import org.locationtech.jts.geom.Envelope;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@State(Scope.Benchmark)
public class RevTreeBuilderBenchmarkState {

    @Param(value = { "512", "100000", "1000000", "5000000", "10000000" })
    public int size;

    @Param(value = { "default", "flatbuffers" })
    public String factory;

    RevObjectFactory revObjectFactory;

    @Param(value = { "heap", "rocksdb" })
    public String storeType;

    private TemporaryFolder tmpFolder;

    private ObjectStore store;

    // private @Getter Str<Node> nodes;

    public @Setup(Level.Trial) void setUp() throws Exception {
        setUpFactory();
        setUpStore();
        // setUpNodes();
    }

    private void setUpStore() throws Exception {
        store = createObjectStore();
        store.open();
    }

    private void setUpFactory() throws Exception {
        String factoryClassName;
        switch (factory) {
        case "default":
            factoryClassName = RevObjectFactoryImpl.class.getName();
            break;
        case "flatbuffers":
            factoryClassName = FlatBuffersRevObjectFactory.class.getName();
            break;
        default:
            throw new IllegalStateException();
        }
        System.setProperty("RevObjectFactory", factoryClassName);
        RevObjectFactory defaultInstance = RevObjectFactory.defaultInstance();
        Preconditions.checkState(defaultInstance.getClass().getName().equals(factoryClassName),
                "String expected RevObjectFactory %s, got %s", factoryClassName,
                defaultInstance.getClass().getName());
        this.revObjectFactory = defaultInstance;
    }

    public @TearDown(Level.Trial) void tearDownStore() throws Exception {
        store.close();
        if (tmpFolder != null) {
            tmpFolder.delete();
        }
    }

    protected ObjectStore createObjectStore() throws Exception {
        switch (storeType) {
        case "heap":
            return new HeapObjectStore();
        case "rocksdb": {
            tmpFolder = new TemporaryFolder();
            tmpFolder.create();
            return new RocksdbObjectDatabase(tmpFolder.getRoot(), false);
        }
        }
        throw new IllegalStateException();
    }

    public Stream<Node> nodes() {
        // this.nodes = nodes(size, FAKE_ID).parallel().collect(Collectors.toList());
        return nodes(size);
    }

    private Stream<Node> nodes(final int numNodes) {
        return IntStream.range(0, numNodes).mapToObj(this::createNode);
    }

    public RevTreeBuilder put(Stream<Node> nodes) {
        RevTreeBuilder builder = CanonicalTreeBuilder.create(store);
        putAll(nodes, builder);
        return builder;
    }

    public RevTree build(RevTreeBuilder builder) {
        return builder.build();
    }

    public RevTree build(Stream<Node> nodes) {
        RevTreeBuilder builder = put(nodes);
        final RevTree tree = build(builder);
        return tree;
    }

    public RevTree update(RevTree tree, Stream<Node> nodes) {
        CanonicalTreeBuilder builder = CanonicalTreeBuilder.create(store, tree);
        putAll(nodes, builder);
        final RevTree updatedTree = builder.build();
        return updatedTree;
    }

    private Node createNode(int i) {
        String name = String.valueOf(i);
        ObjectId id = ObjectId.create(0, 0, i);
        Envelope env = null;// new Envelope(0, 0, i, i);
        Node ref = revObjectFactory.createNode(name, id, ObjectId.NULL, TYPE.FEATURE, env, null);
        return ref;
    }

    private void putAll(final Stream<Node> nodes, final RevTreeBuilder b) {
        nodes.forEach(b::put);
        // int count = 0, s = 0;
        // final int step = nodes.size() / 100;
        //
        // Node n = null;
        // for (Iterator<Node> it = nodes.iterator(); it.hasNext();) {
        // n = it.next();
        // count++;
        // s++;
        // b.put(n);
        // if (s == step) {
        // s = 0;
        // System.err.print('#');
        // }
        // }
        // System.err.println();
        // return count;
    }

}
