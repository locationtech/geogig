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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.collect.Lists;

public class GeoPkgImportTest extends RepositoryTestCase {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private GeogigCLI cli;

    private GeoPackageTestSupport support;

    GeopkgImport importCommand;

    public @Override void setUpInternal() throws Exception {
        importCommand = new GeopkgImport();
        importCommand.commonArgs = new GeopkgCommandProxy();
        Console consoleReader = new Console().disableAnsi();
        cli = new GeogigCLI(consoleReader);

        cli.setGeogig(Geogig.of(repo));

        support = new GeoPackageTestSupport();
    }

    public @Override void tearDownInternal() throws Exception {
        cli.close();
    }

    @Test
    public void testImportTable() throws Exception {
        importCommand.commonArgs.database = support.createDefaultTestData().getAbsolutePath();
        importCommand.table = "Points";
        importCommand.run(cli);

        Iterator<NodeRef> nodeIterator = cli.getGeogig().command(LsTreeOp.class)
                .setStrategy(Strategy.DEPTHFIRST).call();
        assertTrue("Expected repo to have some nodes, but was empty", nodeIterator.hasNext());
        List<String> nodeList = Lists.transform(Lists.newArrayList(nodeIterator),
                (nr) -> nr.name());
        assertTrue(nodeList.contains("Points"));
        assertTrue(nodeList.contains("1"));
        assertTrue(nodeList.contains("2"));
        assertTrue(nodeList.contains("3"));
        assertFalse(nodeList.contains("Lines"));
    }

    @Test
    public void testImportAll() throws Exception {
        importCommand.commonArgs.database = support.createDefaultTestData().getAbsolutePath();
        importCommand.all = true;
        importCommand.run(cli);

        Iterator<NodeRef> nodeIterator = cli.getGeogig().command(LsTreeOp.class)
                .setStrategy(Strategy.DEPTHFIRST).call();
        assertTrue("Expected repo to have some nodes, but was empty", nodeIterator.hasNext());
        List<String> nodeList = Lists.transform(Lists.newArrayList(nodeIterator),
                (nr) -> nr.name());
        // Since there are Lines/1 and Points/1 etc, 1, 2, and 3 should be in the list twice. Remove
        // one after checking the first time.
        assertTrue(nodeList.contains("Points"));
        assertTrue(nodeList.contains("1"));
        nodeList.remove("1");
        assertTrue(nodeList.contains("2"));
        nodeList.remove("2");
        assertTrue(nodeList.contains("3"));
        nodeList.remove("3");
        assertTrue(nodeList.contains("Lines"));
        assertTrue(nodeList.contains("1"));
        assertTrue(nodeList.contains("2"));
        assertTrue(nodeList.contains("3"));
    }

    @Test
    public void testImportFileNotExist() throws Exception {
        importCommand.commonArgs.database = "file://nonexistent.gpkg";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> importCommand.run(cli));
        assertThat(e.getMessage(), containsString("Database file not found."));
    }

}
