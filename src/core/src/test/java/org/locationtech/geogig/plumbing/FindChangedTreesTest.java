/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.geotools.data.DataUtilities;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.di.HintsModule;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.test.MemoryModule;
import org.locationtech.geogig.test.TestPlatform;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Guice;
import com.google.inject.util.Modules;

/**
 *
 */
public class FindChangedTreesTest extends Assert {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private FindChangedTrees command;

    private Repository repo;

    private SimpleFeatureType ftproto;

    public @Before void setUp() throws Exception {

        File workingDirectory = tempFolder.newFolder("mockWorkingDir");
        Platform testPlatform = new TestPlatform(workingDirectory);
        Context injector = Guice
                .createInjector(Modules.override(new GeogigModule()).with(new MemoryModule(),
                        new HintsModule(new Hints().platform(testPlatform))))
                .getInstance(Context.class);

        GeoGIG geogig = new GeoGIG(injector);
        repo = geogig.getOrCreateRepository();

        command = repo.command(FindChangedTrees.class);
        ftproto = DataUtilities.createType("points", "sp:String,ip:Integer,pp:Point:srid=3857");
    }

    public @Test void testNoOldVersionSet() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("old version");
        command.call();
    }

    public @Test void testNoNewVersionSet() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("new version");
        command.setOldTreeIsh(Ref.HEAD).call();
    }

    public @Test void testInvalidOldVersion() {
        exception.expect(IllegalArgumentException.class);
        command.setOldTreeIsh("abcdef0123").setNewTreeIsh(Ref.HEAD).call();
    }

    public @Test void testInvalidNewVersion() {
        exception.expect(IllegalArgumentException.class);
        command.setOldTreeIsh(Ref.HEAD).setNewTreeIsh("abcdef0123").call();
    }

    public @Test void testNullTrees() {
        List<DiffEntry> res = command.setOldTreeIsh(ObjectId.NULL).setNewTreeIsh(ObjectId.NULL)
                .call();
        assertNotNull(res);
        assertTrue(res.isEmpty());
    }

    public @Test void testNoCommitsYet() {
        List<DiffEntry> res = command.setOldTreeIsh(Ref.HEAD).setNewTreeIsh(Ref.HEAD).call();
        assertNotNull(res);
        assertTrue(res.isEmpty());
    }

    public @Test void testDiffLeafRoot() {
        List<NodeRef> layers = createLayers(10);

        WorkingTree workingTree = repo.workingTree();

        RevTree initialRoot = workingTree.getTree();

        NodeRef layer = layers.get(0);
        NodeRef update = putFeatures(layer, 1);

        RevTree finalRoot = workingTree.getTree();

        List<DiffEntry> res = command.setOldTreeIsh(initialRoot.getId())
                .setNewTreeIsh(finalRoot.getId()).call();

        assertEquals(1, res.size());
        DiffEntry entry = res.get(0);
        assertEquals(layer, entry.getOldObject());
        assertEquals(update, entry.getNewObject());
    }

    public @Test void testDiffLeafBucketRoot() {
        List<NodeRef> layers = createLayers(10);

        WorkingTree workingTree = repo.workingTree();

        RevTree initialRoot = workingTree.getTree();

        NodeRef layer = layers.get(0);
        NodeRef update = putFeatures(layer, 1000);

        RevTree finalRoot = workingTree.getTree();

        List<DiffEntry> res = findWithFindChangedTreesCommand(initialRoot, finalRoot);

        assertEquals(1, res.size());
        DiffEntry entry = res.get(0);
        assertEquals(layer, entry.getOldObject());
        assertEquals(update, entry.getNewObject());
    }

    private RevTree setUpMultipleChanges(int numlayers, int numAdded, int numChanged,
            int numDeleted) {
        final List<NodeRef> initialLayers = createLayers(numlayers);

        WorkingTree workingTree = repo.workingTree();

        final RevTree initialRoot = workingTree.getTree();

        final List<NodeRef> addedLayers = createLayers(numlayers, numlayers + numAdded);
        assertEquals(numAdded, addedLayers.size());
        final List<NodeRef> deletedLayers = new ArrayList<>(initialLayers.subList(0, numDeleted));

        final List<NodeRef> changedLayers = new ArrayList<>(
                initialLayers.subList(numlayers - numChanged, numlayers));
        {
            RevTree newWorkhead = workingTree.getTree();

            RevTreeBuilder tb = RevTreeBuilder.builder(repo.objectDatabase(), newWorkhead);
            deletedLayers.forEach(n -> tb.remove(n.getNode()));

            newWorkhead = tb.build();
            workingTree.updateWorkHead(newWorkhead.getId());

            changedLayers.forEach(ref -> putFeatures(ref, 10));
        }
        return initialRoot;
    }

    public @Test void testMultipleChanges() {
        int numlayers = 1000;
        int numAdded = 100;
        int numChanged = 10;
        int numDeleted = 100;

        final RevTree initialRoot = setUpMultipleChanges(numlayers, numAdded, numChanged,
                numDeleted);
        final RevTree finalRoot = repo.workingTree().getTree();

        final List<DiffEntry> res = findWithFindChangedTreesCommand(initialRoot, finalRoot);
        final int expected = numAdded + numChanged + numDeleted;
        assertEquals(expected, res.size());
    }

    public @Test void testDifferentMethodsPerformance() {
        int numlayers = 5_000;
        int numAdded = 2;
        int numChanged = 2;
        int numDeleted = 2;

        final RevTree initialRoot = setUpMultipleChanges(numlayers, numAdded, numChanged,
                numDeleted);
        final RevTree finalRoot = repo.workingTree().getTree();

        Stopwatch swFCT = Stopwatch.createUnstarted();
        Stopwatch swDT = Stopwatch.createUnstarted();
        Stopwatch swLST = Stopwatch.createUnstarted();
        {
            // warm up
            run(() -> findWithFindChangedTreesCommand(initialRoot, finalRoot), 10, swFCT);
            run(() -> findWithDiffTreeCommandReportingOnlyTrees(initialRoot, finalRoot), 10, swDT);
            run(() -> findWithLsTreeOp(initialRoot, finalRoot), 10, swLST);
        }

        IntStream.of(1, 10, 100/* , 1_000 */).forEach(runCount -> {
            final List<DiffEntry> res = run(
                    () -> findWithFindChangedTreesCommand(initialRoot, finalRoot), runCount, swFCT);

            final List<DiffEntry> resDiffTree = run(
                    () -> findWithDiffTreeCommandReportingOnlyTrees(initialRoot, finalRoot),
                    runCount, swDT);
            final List<DiffEntry> resLsTree = run(() -> findWithLsTreeOp(initialRoot, finalRoot),
                    runCount, swLST);

            System.err.printf("Runs: %,d, FindChangedTrees: %s, LsTreeOp: %s, DiffTree: %s\n",
                    runCount, swFCT, swLST, swDT);

            final int expected = numAdded + numChanged + numDeleted;
            assertEquals(expected, res.size());
            assertEquals(expected, resDiffTree.size());
            assertEquals(expected, resLsTree.size());
            assertEquals(Sets.newHashSet(res), Sets.newHashSet(resDiffTree));
            assertEquals(Sets.newHashSet(res), Sets.newHashSet(resLsTree));
        });
    }

    public <T> T run(Supplier<T> command, int times, Stopwatch sw) {
        sw.reset().start();
        T res = null;
        for (int i = 0; i < times; i++) {
            res = command.get();
        }
        sw.stop();
        return res;
    }

    private List<DiffEntry> findWithFindChangedTreesCommand(RevTree left, RevTree right) {
        command = repo.command(FindChangedTrees.class);
        command.setOldTreeIsh(left).setNewTreeIsh(right);
        List<DiffEntry> res = command.call();
        return res;
    }

    private List<DiffEntry> findWithDiffTreeCommandReportingOnlyTrees(RevTree left, RevTree right) {

        try (AutoCloseableIterator<DiffEntry> it = repo.command(DiffTree.class)//
                .setPreserveIterationOrder(false)//
                .setReportFeatures(false)//
                .setReportTrees(true)//
                .setOldTree(left)//
                .setNewTree(right)//
                .call()) {

            return Lists.newArrayList(it);
        }
    }

    private List<DiffEntry> findWithLsTreeOp(RevTree left, RevTree right) {
        LsTreeOp lstreeOld = repo.command(LsTreeOp.class)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_TREES).setReference(left.getId().toString());
        LsTreeOp lstreeNew = repo.command(LsTreeOp.class)
                .setStrategy(Strategy.DEPTHFIRST_ONLY_TREES).setReference(right.getId().toString());
        Iterator<NodeRef> oldtrees = lstreeOld.call();
        Iterator<NodeRef> newtrees = lstreeNew.call();

        ImmutableMap<String, NodeRef> oldtreemap = Maps.uniqueIndex(oldtrees, (r) -> r.path());
        ImmutableMap<String, NodeRef> newtreemap = Maps.uniqueIndex(newtrees, (r) -> r.path());

        MapDifference<String, NodeRef> difference = Maps.difference(oldtreemap, newtreemap);
        List<DiffEntry> res = new ArrayList<>();

        difference.entriesOnlyOnLeft().forEach((p, e) -> res.add(new DiffEntry(e, null)));
        difference.entriesOnlyOnRight().forEach((p, e) -> res.add(new DiffEntry(null, e)));
        difference.entriesDiffering()
                .forEach((p, v) -> res.add(new DiffEntry(v.leftValue(), v.rightValue())));
        return res;
    }

    private NodeRef putFeatures(NodeRef layer, int featureCount) {
        List<FeatureInfo> features = createFeatures(layer, featureCount);
        repo.workingTree().insert(features.iterator(), new DefaultProgressListener());
        Optional<NodeRef> ref = repo.command(FindTreeChild.class)
                .setParent(repo.workingTree().getTree()).setChildPath(layer.path()).call();
        assertTrue(ref.isPresent());
        return ref.get();
    }

    private List<FeatureInfo> createFeatures(NodeRef layer, int featureCount) {
        List<FeatureInfo> features = new ArrayList<>();
        ObjectId featureTypeId = layer.getDefaultMetadataId();

        for (int i = 0; i < featureCount; i++) {
            String fid = String.valueOf(i);
            RevFeature feature = RevFeature.builder().addValue(fid).addValue(i).addValue(null)
                    .build();
            String path = NodeRef.appendChild(layer.path(), fid);
            FeatureInfo featureInfo = FeatureInfo.insert(feature, featureTypeId, path);
            features.add(featureInfo);
        }
        return features;
    }

    private List<NodeRef> createLayers(int count) {
        return createLayers(0, count);
    }

    private List<NodeRef> createLayers(int base, int count) {
        SimpleFeatureTypeBuilder ftb = new SimpleFeatureTypeBuilder();
        Map<String, SimpleFeatureType> types = new HashMap<>();
        for (int i = base; i < count; i++) {
            String treePath = "tree" + i;
            ftb.setName(treePath);
            ftb.addAll(ftproto.getAttributeDescriptors());
            SimpleFeatureType featureType = ftb.buildFeatureType();
            types.put(treePath, featureType);
        }
        return createLayers(types);
    }

    private List<NodeRef> createLayers(Map<String, SimpleFeatureType> types) {
        List<NodeRef> layers = new ArrayList<>();
        WorkingTree workingTree = repo.workingTree();

        repo.objectDatabase().putAll(Iterators.transform(types.values().iterator(),
                (t) -> RevFeatureType.builder().type(t).build()));

        RevTree wt = workingTree.getTree();
        RevTreeBuilder newWorkHeadBuilder = RevTreeBuilder.builder(repo.objectDatabase(), wt);

        types.forEach((treePath, featureType) -> {
            ObjectId mdId = RevFeatureType.builder().type(featureType).build().getId();
            Node node = RevObjectFactory.defaultInstance().createNode(treePath,
                    RevTree.EMPTY_TREE_ID, mdId, TYPE.TREE, null, null);
            newWorkHeadBuilder.put(node);
            NodeRef nodeRef = NodeRef.create(NodeRef.ROOT, node);
            layers.add(nodeRef);
        });

        RevTree newWorkHead = newWorkHeadBuilder.build();
        workingTree.updateWorkHead(newWorkHead.getId());
        return layers;
    }
}
