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

import static com.google.common.collect.Lists.newArrayList;

import java.io.File;
import java.util.List;

import org.geotools.data.DataUtilities;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.di.HintsModule;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.test.MemoryModule;
import org.locationtech.geogig.test.TestPlatform;
import org.locationtech.jts.geom.Envelope;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.inject.Guice;
import com.google.inject.util.Modules;

/**
 *
 */
public class DiffTreeTest extends Assert {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private DiffTree diffTree;

    private GeoGIG geogig;

    private RevFeatureType revtype;

    private ObjectId metadataId;

    @Before
    public void setUp() throws Exception {

        File workingDirectory = tempFolder.newFolder("mockWorkingDir");
        Platform testPlatform = new TestPlatform(workingDirectory);
        Context injector = Guice
                .createInjector(Modules.override(new GeogigModule()).with(new MemoryModule(),
                        new HintsModule(new Hints().platform(testPlatform))))
                .getInstance(Context.class);

        geogig = new GeoGIG(injector);
        assertNotNull(geogig.getOrCreateRepository());
        diffTree = geogig.command(DiffTree.class);

        SimpleFeatureType ft = DataUtilities.createType("points",
                "sp:String,ip:Integer,pp:Point:srid=3857");
        revtype = RevFeatureType.builder().type(ft).build();
        metadataId = revtype.getId();
        geogig.getContext().objectDatabase().put(revtype);
    }

    @Test
    public void testNoOldVersionSet() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("old version");
        diffTree.call();
    }

    @Test
    public void testNoNewVersionSet() {
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("new version");
        diffTree.setOldVersion(Ref.HEAD).call();
    }

    @Test
    public void testInvalidOldVersion() {
        exception.expect(IllegalArgumentException.class);
        diffTree.setOldVersion("abcdef0123").setNewVersion(Ref.HEAD).call();
    }

    @Test
    public void testInvalidNewVersion() {
        exception.expect(IllegalArgumentException.class);
        diffTree.setOldVersion(Ref.HEAD).setNewVersion("abcdef0123").call();
    }

    @Test
    public void testNullTrees() {
        try (AutoCloseableIterator<DiffEntry> diffs = diffTree.setOldTree(ObjectId.NULL)
                .setNewTree(ObjectId.NULL).call()) {
            assertFalse(diffs.hasNext());
        }
    }

    @Test
    public void testNoCommitsYet() {
        assertFalse(diffTree.setOldVersion(Ref.HEAD).setNewVersion(Ref.HEAD).call().hasNext());
    }

    @Test
    public void testTreePathFiltering() {
        ObjectDatabase db = geogig.getContext().objectDatabase();
        RevTree tree1 = tree(100, db);
        RevTree tree2 = tree(50, db);
        RevTree root = createRoot(db, tree1, tree2);

        List<String> pathFilters = ImmutableList.of("tree1");
        diffTree.setOldTree(ObjectId.NULL).setNewTree(root.getId()).setPathFilter(pathFilters);
        List<DiffEntry> diffs = ImmutableList.copyOf(diffTree.call());
        assertEquals(tree1.size(), diffs.size());

        pathFilters = ImmutableList.of("tree2");
        diffTree.setOldTree(ObjectId.NULL).setNewTree(root.getId()).setPathFilter(pathFilters);
        diffs = ImmutableList.copyOf(diffTree.call());
        assertEquals(tree2.size(), diffs.size());

        pathFilters = ImmutableList.of("tree1/1", "tree1/2", "tree1/3", "tree1/4", "tree2/2",
                "tree2/3", "tree2/10");
        diffTree.setOldTree(ObjectId.NULL).setNewTree(root.getId()).setPathFilter(pathFilters);
        diffs = ImmutableList.copyOf(diffTree.call());
        assertEquals(pathFilters.size(), diffs.size());
    }

    @Test
    public void testBoundsFiltering() {
        ObjectDatabase db = geogig.getContext().objectDatabase();
        RevTree tree1 = tree(1000, db);
        RevTree tree2 = tree(50, db);
        RevTree root = createRoot(db, tree1, tree2);

        CoordinateReferenceSystem crs = revtype.type().getCoordinateReferenceSystem();
        ReferencedEnvelope filter;
        List<DiffEntry> diffs;

        diffTree.setOldTree(ObjectId.NULL).setNewTree(root.getId());

        filter = new ReferencedEnvelope(50, 51, 50, 51, crs);
        diffTree.setBoundsFilter(filter);
        diffs = ImmutableList.copyOf(diffTree.call());
        assertEquals(2, diffs.size());
    }

    @Test
    public void testBoundsFilteringReprojecting() throws Exception {
        ObjectDatabase db = geogig.getContext().objectDatabase();
        RevTree tree1 = tree(1000, db);
        RevTree tree2 = tree(50, db);
        RevTree root = createRoot(db, tree1, tree2);

        CoordinateReferenceSystem nativeCrs = revtype.type().getCoordinateReferenceSystem();
        CoordinateReferenceSystem queryCrs = CRS.decode("EPSG:4326", true);

        ReferencedEnvelope nativeFilter = new ReferencedEnvelope(49.9, 51.1, 49.9, 51.1, nativeCrs);
        ReferencedEnvelope queryFilter = nativeFilter.transform(queryCrs, true);
        List<DiffEntry> diffs;

        diffTree.setOldTree(ObjectId.NULL).setNewTree(root.getId());

        diffTree.setBoundsFilter(queryFilter);
        diffs = ImmutableList.copyOf(diffTree.call());
        assertEquals(2, diffs.size());
    }

    @Test
    public void testChangeTypeFilter() {
        ObjectDatabase db = geogig.getContext().objectDatabase();
        final RevTree tree1 = tree(1000, db);
        final RevTree tree2 = tree(50, db);
        final RevTree tree2Changed;
        {
            RevTreeBuilder builder = RevTreeBuilder.builder(db, tree2);
            // add 10 changed features, and delete 10 more
            for (int i = 0; i < 20; i++) {
                if (i % 2 == 0) {
                    builder.remove(node(String.valueOf(i)));
                } else {
                    builder.put(feature(i, RevObjectTestSupport.hashString("changed" + i)));
                }
            }
            tree2Changed = builder.build();
            db.put(tree2Changed);
            assertEquals(tree2.size() - 10, tree2Changed.size());
        }
        final RevTree root1 = createRoot(db, tree1, tree2);
        final RevTree root2 = createRoot(db, tree1, tree2Changed);

        assertChangeTypeFilter(ObjectId.NULL, root1.getId(), (int) (tree1.size() + tree2.size()), 0,
                0);
        assertChangeTypeFilter(root1.getId(), ObjectId.NULL, 0, (int) (tree1.size() + tree2.size()),
                0);

        assertChangeTypeFilter(tree2.getId(), tree2Changed.getId(), 0, 10, 10);
        assertChangeTypeFilter(root1.getId(), root2.getId(), 0, 10, 10);
        assertChangeTypeFilter(root2.getId(), root1.getId(), 10, 0, 10);
    }

    private Node node(String id) {
        return RevObjectFactory.defaultInstance().createNode(id, ObjectId.NULL, ObjectId.NULL,
                TYPE.FEATURE, null, null);

    }

    /**
     * Apply path, bounds, and changeType filtering all at once
     */
    @Test
    public void testMixedFilters() {
        ObjectDatabase db = geogig.getContext().objectDatabase();
        final RevTree tree1 = tree(1000, db);
        final RevTree tree2 = tree(50, db);
        final RevTree tree2Changed;
        {
            RevTreeBuilder builder = RevTreeBuilder.builder(db, tree2);
            // add 10 changed features, and delete 10 more
            for (int i = 0; i < 20; i++) {
                if (i % 2 == 0) {
                    builder.remove(node(String.valueOf(i)));
                } else {
                    builder.put(feature(i, RevObjectTestSupport.hashString("changed" + i)));
                }
            }
            tree2Changed = builder.build();
            db.put(tree2Changed);
            assertEquals(tree2.size() - 10, tree2Changed.size());
        }
        final RevTree root1 = createRoot(db, tree1, tree2);
        final RevTree root2 = createRoot(db, tree1, tree2Changed);
        final ObjectId rootId1 = root1.getId();
        final ObjectId rootId2 = root2.getId();

        CoordinateReferenceSystem crs = revtype.type().getCoordinateReferenceSystem();
        // boundsFilter covers features 1-11
        ReferencedEnvelope boundsFilter = new ReferencedEnvelope(1.9, 11.1, 1.9, 11.1, crs);

        // first try with bounds only
        diffTree.setBoundsFilter(boundsFilter);
        assertEquals(10, Iterators.size(diffTree.setOldTree(rootId1).setNewTree(rootId2).call()));

        assertChangeTypeFilter(rootId1, rootId2, 0, 5, 5);
        assertChangeTypeFilter(rootId2, rootId1, 5, 0, 5);

        // now add path filtering
        diffTree.setPathFilter("tree1");
        assertChangeTypeFilter(rootId1, rootId2, 0, 0, 0);
        assertChangeTypeFilter(rootId2, rootId1, 0, 0, 0);

        diffTree.setPathFilter("tree2");
        assertChangeTypeFilter(rootId1, rootId2, 0, 5, 5);
        assertChangeTypeFilter(rootId2, rootId1, 5, 0, 5);

        // odd feature ids from 0 to 18 were removed from tree2
        // tree2/0 and tree2/12 match path filter but don't match bounds filter
        diffTree.setPathFilter(ImmutableList.of("tree2/0", "tree2/2", "tree2/4", "tree2/12"));

        diffTree.setBoundsFilter(null);
        assertChangeTypeFilter(rootId1, rootId2, 0, 4, 0);
        assertChangeTypeFilter(rootId2, rootId1, 4, 0, 0);

        diffTree.setBoundsFilter(boundsFilter);
        assertChangeTypeFilter(rootId1, rootId2, 0, 2, 0);
        assertChangeTypeFilter(rootId2, rootId1, 2, 0, 0);
    }

    private void assertChangeTypeFilter(final ObjectId leftTree, final ObjectId rightTree,
            final int expectedAdds, final int expectedRemoves, final int expectedChanges) {

        List<DiffEntry> list;

        diffTree.setOldTree(leftTree).setNewTree(rightTree);

        diffTree.setChangeTypeFilter(ChangeType.ADDED);
        list = newArrayList(diffTree.call());
        assertEquals(list.toString(), expectedAdds, list.size());

        diffTree.setChangeTypeFilter(ChangeType.REMOVED);
        list = newArrayList(diffTree.call());
        assertEquals(list.toString(), expectedRemoves, list.size());

        diffTree.setChangeTypeFilter(ChangeType.MODIFIED);
        list = newArrayList(diffTree.call());
        assertEquals(list.toString(), expectedChanges, list.size());
    }

    private RevTree createRoot(ObjectDatabase db, final RevTree tree1, final RevTree tree2) {
        RevTreeBuilder rootBuilder = RevTreeBuilder.builder(db);
        rootBuilder.put(RevObjectFactory.defaultInstance().createNode("tree1", tree1.getId(),
                metadataId, TYPE.TREE, SpatialOps.boundsOf(tree1), null));
        rootBuilder.put(RevObjectFactory.defaultInstance().createNode("tree2", tree2.getId(),
                metadataId, TYPE.TREE, SpatialOps.boundsOf(tree2), null));
        RevTree root = rootBuilder.build();
        db.put(root);
        return root;
    }

    private RevTree tree(int nFeatures, ObjectDatabase db) {
        RevTreeBuilder b = RevTreeBuilder.builder(db);
        for (int i = 0; i < nFeatures; i++) {
            b.put(feature(i));
        }
        RevTree tree = b.build();
        db.put(tree);
        return tree;
    }

    private Node feature(int i) {
        return feature(i, RevObjectTestSupport.hashString(String.valueOf(i)));
    }

    private Node feature(int i, ObjectId oid) {
        String name = String.valueOf(i);
        TYPE type = TYPE.FEATURE;
        Envelope bounds = new Envelope(i, i, i, i);
        return RevObjectFactory.defaultInstance().createNode(name, oid, metadataId, type, bounds,
                null);
    }
}
