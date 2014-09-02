/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.osm.cli.commands;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import jline.UnsupportedTerminal;
import jline.console.ConsoleReader;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.api.plumbing.LsTreeOp;
import org.locationtech.geogig.api.plumbing.ResolveFeatureType;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.test.functional.general.CLITestContextBuilder;
import org.locationtech.geogig.osm.internal.OSMImportOp;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Polygon;

public class OSMMapTest extends Assert {

    private GeogigCLI cli;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        ConsoleReader consoleReader = new ConsoleReader(System.in, System.out,
                new UnsupportedTerminal());
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
    public void testMapping() throws Exception {
        // import and check
        String filename = OSMImportOp.class.getResource("ways.xml").getFile();
        File file = new File(filename);
        cli.execute("osm", "import", file.getAbsolutePath());
        cli.execute("add");
        cli.execute("commit", "-m", "message");
        GeoGIG geogig = cli.newGeoGIG();
        Optional<RevTree> tree = geogig.command(RevObjectParse.class).setRefSpec("HEAD:node")
                .call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        tree = geogig.command(RevObjectParse.class).setRefSpec("HEAD:way").call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        // map
        String mappingFilename = OSMMap.class.getResource("mapping.json").getFile();
        File mappingFile = new File(mappingFilename);
        cli.execute("osm", "map", mappingFile.getAbsolutePath());
        // check that a feature was correctly mapped
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:onewaystreets/31045880").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        ImmutableList<Optional<Object>> values = revFeature.get().getValues();
        String wkt = "LINESTRING (7.1923367 50.7395887, 7.1923127 50.7396946, 7.1923444 50.7397419, 7.1924199 50.7397781)";
        assertEquals(wkt, values.get(2).get().toString());
        assertEquals("345117525;345117526;1300224327;345117527", values.get(3).get());
        assertEquals("yes", values.get(1).get());
        // check that a feature was correctly ignored
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("HEAD:onewaystreets/31347480")
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());
        geogig.close();
    }

    @Test
    public void testMappingWithWrongMappingFile() throws Exception {
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
        String mappingFilename = OSMMap.class.getResource("wrong_mapping.json").getFile();
        File mappingFile = new File(mappingFilename);

        int retcode = cli.execute("osm", "map", mappingFile.getAbsolutePath());
        assertTrue(retcode != 0);
        assertNotNull(cli.exception);
        assertTrue(cli.exception.getMessage().startsWith("Error parsing mapping definition"));
    }

    @Test
    public void testMappingWithMissingFile() throws Exception {
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

        cli.execute("osm", "map", "awrongpath/awroongfile.json");
        assertNotNull(cli.exception);
        assertTrue(cli.exception.getMessage().startsWith(
                "The specified mapping file does not exist"));

    }

    @Test
    public void testMappingWithNoFilter() throws Exception {
        // check that if no filter is passed, all entities are mapped
        String filename = OSMImportOp.class.getResource("ways.xml").getFile();
        File file = new File(filename);
        cli.execute("osm", "import", file.getAbsolutePath());
        cli.execute("add");
        cli.execute("commit", "-m", "message");
        GeoGIG geogig = cli.newGeoGIG();
        Optional<RevTree> tree = geogig.command(RevObjectParse.class).setRefSpec("HEAD:node")
                .call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        tree = geogig.command(RevObjectParse.class).setRefSpec("HEAD:way").call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        String mappingFilename = OSMMap.class.getResource("no_filter_mapping.json").getFile();
        File mappingFile = new File(mappingFilename);
        cli.execute("osm", "map", mappingFile.getAbsolutePath());
        Iterator<NodeRef> allways = geogig.command(LsTreeOp.class).setReference("HEAD:all_ways")
                .call();
        ArrayList<NodeRef> listAllways = Lists.newArrayList(allways);
        assertEquals(4, listAllways.size());
        geogig.close();
    }

    @Test
    public void testMappingWithPolygons() throws Exception {
        // test a mapping with a a mapping rule that uses the polygon geometry type
        String filename = OSMImportOp.class.getResource("closed_ways.xml").getFile();
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
        String mappingFilename = OSMMap.class.getResource("polygons_mapping.json").getFile();
        File mappingFile = new File(mappingFilename);
        cli.execute("osm", "map", mappingFile.getAbsolutePath());
        Iterator<NodeRef> iter = cli.getGeogig().command(LsTreeOp.class).setReference("HEAD:areas")
                .call();
        assertTrue(iter.hasNext());
        Optional<RevFeatureType> ft = cli.getGeogig().command(ResolveFeatureType.class)
                .setRefSpec("HEAD:" + iter.next().path()).call();
        assertTrue(ft.isPresent());
        assertEquals(Polygon.class, ft.get().sortedDescriptors().get(1).getType().getBinding());

    }

    @Test
    public void testMappingWithDirtyWorkingTree() throws Exception {
    }

    @Test
    public void testMappingWithNoChanges() throws Exception {
        String filename = OSMImportOp.class.getResource("ways.xml").getFile();
        File file = new File(filename);
        String mappingFilename = OSMMap.class.getResource("mapping.json").getFile();
        File mappingFile = new File(mappingFilename);
        cli.execute("osm", "import", file.getAbsolutePath(), "--mapping",
                mappingFile.getAbsolutePath());
        Optional<RevFeatureType> revFeatureType = cli.getGeogig().command(ResolveFeatureType.class)
                .setRefSpec("onewaystreets").call();
        assertTrue(revFeatureType.isPresent());
        cli.execute("osm", "map", mappingFile.getAbsolutePath());

    }

    @Test
    public void testMappingExcludingFeaturesWithMissingTag() throws Exception {
        // import and check
        String filename = OSMImportOp.class.getResource("ways.xml").getFile();
        File file = new File(filename);
        cli.execute("osm", "import", file.getAbsolutePath());
        cli.execute("add");
        cli.execute("commit", "-m", "message");
        GeoGIG geogig = cli.newGeoGIG();
        Optional<RevTree> tree = geogig.command(RevObjectParse.class).setRefSpec("HEAD:node")
                .call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        tree = geogig.command(RevObjectParse.class).setRefSpec("HEAD:way").call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        // map
        String mappingFilename = OSMMapTest.class.getResource("mapping_exclude_missing_tag.json")
                .getFile();
        File mappingFile = new File(mappingFilename);
        cli.execute("osm", "map", mappingFile.getAbsolutePath());
        // check that a feature was correctly mapped
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:namedhighways/2059114068").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        // check that a feature was correctly ignored
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("HEAD:namedhighways/81953612")
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());
        geogig.close();

    }
}
