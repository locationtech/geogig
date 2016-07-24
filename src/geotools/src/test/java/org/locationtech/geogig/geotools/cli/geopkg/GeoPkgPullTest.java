/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geopkg;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class GeoPkgPullTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private GeogigCLI cli;

    private GeoPackageTestSupport support;

    @Override
    public void setUpInternal() throws Exception {
        setupCLI();
    }

    private void setupCLI() {
        Console consoleReader = new Console().disableAnsi();
        cli = new GeogigCLI(consoleReader);

        cli.setGeogig(geogig);

        support = new GeoPackageTestSupport();
    }

    @Override
    public void tearDownInternal() throws Exception {
        cli.close();
    }

    @Test
    public void testPull() throws Exception {
        // Add points
        insertAndAdd(points1);
        insertAndAdd(points2);
        insertAndAdd(points3);

        geogig.command(CommitOp.class).call();

        GeopkgExport exportCommand = new GeopkgExport();
        File geoPkgFile = support.newFile();
        String geoPkgFileName = geoPkgFile.getAbsolutePath();
        exportCommand.args = Arrays.asList("Points", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.interchangeFormat = true;
        exportCommand.run(cli);

        DataStore gpkgStore = store(geoPkgFile);

        // Add a point to the geopackage
        Transaction gttx = new DefaultTransaction();
        try {
            SimpleFeatureStore store = (SimpleFeatureStore) gpkgStore.getFeatureSource("Points");
            Preconditions.checkState(store.getQueryCapabilities().isUseProvidedFIDSupported());
            store.setTransaction(gttx);
            SimpleFeature points4 = (SimpleFeature) feature(pointsType, "Points.4", "StringProp1_4",
                    new Integer(4000), "POINT(4 4)");
            store.addFeatures(DataUtilities.collection(points4));
            gttx.commit();
        } finally {
            gttx.close();
            gpkgStore.dispose();
        }

        GeopkgPull pullCommand = new GeopkgPull();
        pullCommand.commonArgs.database = geoPkgFileName;
        pullCommand.commitMessage = "Imported from geopackage.";
        pullCommand.table = "Points";
        pullCommand.run(cli);

        Iterator<NodeRef> nodeIterator = cli.getGeogig().command(LsTreeOp.class)
                .setStrategy(Strategy.DEPTHFIRST).call();
        assertTrue("Expected repo to have some nodes, but was empty", nodeIterator.hasNext());
        List<String> nodeList = Lists.transform(Lists.newArrayList(nodeIterator),
                (nr) -> nr.name());
        assertEquals(5, nodeList.size());
        assertTrue(nodeList.contains(pointsType.getTypeName()));
        nodeList.remove(pointsType.getTypeName());
        assertTrue(nodeList.contains(idP1));
        nodeList.remove(idP1);
        assertTrue(nodeList.contains(idP2));
        nodeList.remove(idP2);
        assertTrue(nodeList.contains(idP3));
        nodeList.remove(idP3);
        // last node was the newly added one, it'll have a randomly generated fid
        assertTrue(nodeList.get(0).startsWith("fid-"));

        RevCommit latestCommit = geogig.command(RevObjectParse.class).setRefSpec("HEAD")
                .call(RevCommit.class).get();
        assertEquals(pullCommand.commitMessage, latestCommit.getMessage());
    }

    @Test
    public void testPullMerge() throws Exception {
        // Add points
        insertAndAdd(points1);
        insertAndAdd(points2);
        insertAndAdd(points3);

        geogig.command(CommitOp.class).call();

        GeopkgExport exportCommand = new GeopkgExport();
        File geoPkgFile = support.newFile();
        String geoPkgFileName = geoPkgFile.getAbsolutePath();
        exportCommand.args = Arrays.asList("Points", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.interchangeFormat = true;
        exportCommand.run(cli);

        // Add lines
        insertAndAdd(lines1);
        insertAndAdd(lines2);
        insertAndAdd(lines3);

        geogig.command(CommitOp.class).call();

        DataStore gpkgStore = store(geoPkgFile);

        // Add a point to the geopackage
        Transaction gttx = new DefaultTransaction();
        try {
            SimpleFeatureStore store = (SimpleFeatureStore) gpkgStore.getFeatureSource("Points");
            Preconditions.checkState(store.getQueryCapabilities().isUseProvidedFIDSupported());
            store.setTransaction(gttx);
            SimpleFeature points4 = (SimpleFeature) feature(pointsType, "Points.4", "StringProp1_4",
                    new Integer(4000), "POINT(4 4)");
            store.addFeatures(DataUtilities.collection(points4));
            gttx.commit();
        } finally {
            gttx.close();
            gpkgStore.dispose();
        }

        GeopkgPull pullCommand = new GeopkgPull();
        pullCommand.commonArgs.database = geoPkgFileName;
        pullCommand.commitMessage = "Imported from geopackage.";
        pullCommand.table = "Points";
        pullCommand.run(cli);

        Iterator<NodeRef> nodeIterator = cli.getGeogig().command(LsTreeOp.class)
                .setStrategy(Strategy.DEPTHFIRST).call();
        assertTrue("Expected repo to have some nodes, but was empty", nodeIterator.hasNext());
        List<String> nodeList = Lists.transform(Lists.newArrayList(nodeIterator),
                (nr) -> nr.name());
        assertEquals(9, nodeList.size());
        assertTrue(nodeList.contains(pointsType.getTypeName()));
        nodeList.remove(pointsType.getTypeName());
        assertTrue(nodeList.contains(idP1));
        nodeList.remove(idP1);
        assertTrue(nodeList.contains(idP2));
        nodeList.remove(idP2);
        assertTrue(nodeList.contains(idP3));
        nodeList.remove(idP3);
        assertTrue(nodeList.contains(linesType.getTypeName()));
        nodeList.remove(linesType.getTypeName());
        assertTrue(nodeList.contains(idL1));
        nodeList.remove(idL1);
        assertTrue(nodeList.contains(idL2));
        nodeList.remove(idL2);
        assertTrue(nodeList.contains(idL3));
        nodeList.remove(idL3);
        // last node was the newly added one, it'll have a randomly generated fid
        assertTrue(nodeList.get(0).startsWith("fid-"));

        RevCommit latestCommit = geogig.command(RevObjectParse.class).setRefSpec("HEAD")
                .call(RevCommit.class).get();
        assertEquals("Merge: " + pullCommand.commitMessage, latestCommit.getMessage());
    }

    @Test
    public void testPullConflict() throws Exception {
        // Add points
        insertAndAdd(points1);
        insertAndAdd(points2);
        insertAndAdd(points3);

        geogig.command(CommitOp.class).call();

        GeopkgExport exportCommand = new GeopkgExport();
        File geoPkgFile = support.newFile();
        String geoPkgFileName = geoPkgFile.getAbsolutePath();
        exportCommand.args = Arrays.asList("Points", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.interchangeFormat = true;
        exportCommand.run(cli);

        // Add lines
        deleteAndAdd(points1);

        geogig.command(CommitOp.class).call();

        DataStore gpkgStore = store(geoPkgFile);

        // Modify a point to the geopackage
        Transaction gttx = new DefaultTransaction();
        try {
            SimpleFeatureStore store = (SimpleFeatureStore) gpkgStore.getFeatureSource("Points");
            Preconditions.checkState(store.getQueryCapabilities().isUseProvidedFIDSupported());
            store.setTransaction(gttx);
            store.modifyFeatures("ip", ((SimpleFeature) points1_modified).getAttribute("ip"),
                    Filter.INCLUDE);
            gttx.commit();
        } finally {
            gttx.close();
            gpkgStore.dispose();
        }

        GeopkgPull pullCommand = new GeopkgPull();
        pullCommand.commonArgs.database = geoPkgFileName;
        pullCommand.commitMessage = "Imported from geopackage.";
        pullCommand.table = "Points";
        exception.expect(CommandFailedException.class);
        exception.expectMessage("CONFLICT: Merge conflict");
        pullCommand.run(cli);
    }

    @Test
    public void testPullFileNotExist() throws Exception {
        GeopkgPull pullCommand = new GeopkgPull();
        pullCommand.commonArgs.database = "file://nonexistent.gpkg";
        exception.expect(IllegalArgumentException.class);
        exception.expectMessage("Database file not found.");
        pullCommand.run(cli);
    }

    private DataStore store(File result) throws InterruptedException, ExecutionException {
        assertNotNull(result);
        return support.createDataStore(result);
    }

}
