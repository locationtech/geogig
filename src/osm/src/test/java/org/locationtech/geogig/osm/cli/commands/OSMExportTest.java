/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.cli.commands;

import java.io.File;
import java.util.Iterator;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.porcelain.DiffOp;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.test.functional.CLITestContextBuilder;
import org.locationtech.geogig.osm.internal.OSMImportOp;

import com.google.common.base.Optional;

public class OSMExportTest extends Assert {

    private GeogigCLI cli;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        Console consoleReader = new Console().disableAnsi();
        cli = new GeogigCLI(consoleReader);
        File workingDirectory = tempFolder.getRoot();
        TestPlatform platform = new TestPlatform(workingDirectory);
        GlobalContextBuilder.builder = new CLITestContextBuilder(platform);
        cli.setPlatform(platform);
        cli.execute("init");
        cli.execute("config", "user.name", "Gabriel Roldan");
        cli.execute("config", "user.email", "groldan@boundlessgeo.com");
        assertTrue(new File(workingDirectory, ".geogig").exists());

    }

    @Test
    public void testExportOnlyNodes() throws Exception {
        String filename = OSMImportOp.class.getResource("nodes.xml").getFile();
        File file = new File(filename);
        cli.execute("osm", "import", file.getAbsolutePath());
        cli.execute("add");
        cli.execute("commit", "-m", "message");
        Optional<RevTree> tree = cli.getGeogig().command(RevObjectParse.class)
                .setRefSpec("HEAD:node").call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        File exportFile = new File(tempFolder.getRoot(), "export.xml");
        cli.execute("osm", "export", exportFile.getAbsolutePath());
    }

    @Test
    public void testExportAndThenReimport() throws Exception {
        String filename = OSMImportOp.class.getResource("fire.xml").getFile();
        File filterFile = new File(filename);
        cli.execute("osm", "import", filterFile.getAbsolutePath());
        cli.execute("add");
        cli.execute("commit", "-m", "message");
        Optional<ObjectId> id = cli.getGeogig().command(RevParse.class).setRefSpec("HEAD:node")
                .call();
        assertTrue(id.isPresent());
        id = cli.getGeogig().command(RevParse.class).setRefSpec("HEAD:way").call();
        assertTrue(id.isPresent());
        File file = new File(tempFolder.getRoot(), "export.xml");
        cli.execute("osm", "export", file.getAbsolutePath());
        cli.execute("rm", "-r", "node");
        cli.execute("rm", "-r", "way");
        cli.execute("add");
        cli.execute("commit", "-m", "Deleted OSM data");
        id = cli.getGeogig().command(RevParse.class).setRefSpec("HEAD:node").call();
        assertFalse(id.isPresent());
        id = cli.getGeogig().command(RevParse.class).setRefSpec("HEAD:way").call();
        assertFalse(id.isPresent());
        cli.execute("osm", "import", file.getAbsolutePath());
        cli.execute("add");
        cli.execute("commit", "-m", "reimport");
        Optional<RevTree> tree = cli.getGeogig().command(RevObjectParse.class)
                .setRefSpec("HEAD:node").call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        tree = cli.getGeogig().command(RevObjectParse.class).setRefSpec("HEAD:way")
                .call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        Iterator<DiffEntry> diffs = cli.getGeogig().command(DiffOp.class).setNewVersion("HEAD")
                .setOldVersion("HEAD~2").call();
        assertFalse(diffs.hasNext());
    }

    @Test
    public void testExportFromWorkingHead() throws Exception {
        String filename = OSMImportOp.class.getResource("ways.xml").getFile();
        File file = new File(filename);
        cli.execute("osm", "import", file.getAbsolutePath());
        cli.execute("add");
        cli.execute("commit", "-m", "message");
        Optional<RevTree> tree = cli.getGeogig().command(RevObjectParse.class)
                .setRefSpec("HEAD:node").call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        tree = cli.getGeogig().command(RevObjectParse.class).setRefSpec("HEAD:way")
                .call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        File exportFile = new File(tempFolder.getRoot(), "export.xml");
        cli.execute("osm", "export", exportFile.getAbsolutePath(), "WORK_HEAD");
        cli.execute("rm", "-r", "node");
        cli.execute("rm", "-r", "way");
        tree = cli.getGeogig().command(RevObjectParse.class).setRefSpec("WORK_HEAD:node")
                .call(RevTree.class);
        assertFalse(tree.isPresent());
        tree = cli.getGeogig().command(RevObjectParse.class).setRefSpec("WORK_HEAD:way")
                .call(RevTree.class);
        assertFalse(tree.isPresent());
        cli.execute("osm", "import", exportFile.getAbsolutePath());
        tree = cli.getGeogig().command(RevObjectParse.class).setRefSpec("HEAD:node")
                .call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        tree = cli.getGeogig().command(RevObjectParse.class).setRefSpec("HEAD:way")
                .call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
    }

    @Test
    public void testExportAndThenReimportUsingPbfFormat() throws Exception {
        String filename = OSMImportOp.class.getResource("fire.xml").getFile();
        File filterFile = new File(filename);
        cli.execute("osm", "import", filterFile.getAbsolutePath());
        cli.execute("add");
        cli.execute("commit", "-m", "message");
        Optional<ObjectId> id = cli.getGeogig().command(RevParse.class).setRefSpec("HEAD:node")
                .call();
        assertTrue(id.isPresent());
        id = cli.getGeogig().command(RevParse.class).setRefSpec("HEAD:way").call();
        assertTrue(id.isPresent());
        File file = new File(tempFolder.getRoot(), "export.pbf");
        cli.execute("osm", "export", file.getAbsolutePath());
        cli.execute("rm", "-r", "node");
        cli.execute("rm", "-r", "way");
        cli.execute("add");
        cli.execute("commit", "-m", "Deleted OSM data");
        id = cli.getGeogig().command(RevParse.class).setRefSpec("HEAD:node").call();
        assertFalse(id.isPresent());
        id = cli.getGeogig().command(RevParse.class).setRefSpec("HEAD:way").call();
        assertFalse(id.isPresent());
        cli.execute("osm", "import", file.getAbsolutePath());
        cli.execute("add");
        cli.execute("commit", "-m", "reimport");
        Optional<RevTree> tree = cli.getGeogig().command(RevObjectParse.class)
                .setRefSpec("HEAD:node").call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        tree = cli.getGeogig().command(RevObjectParse.class).setRefSpec("HEAD:way")
                .call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
    }

}
