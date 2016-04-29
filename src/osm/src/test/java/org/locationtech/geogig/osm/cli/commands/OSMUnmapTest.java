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
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.osm.internal.OSMImportOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class OSMUnmapTest extends Assert {

    private GeogigCLI cli;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        Console consoleReader = new Console().disableAnsi();
        cli = new GeogigCLI(consoleReader);
        File workingDirectory = tempFolder.getRoot();
        TestPlatform platform = new TestPlatform(workingDirectory);
        cli.setPlatform(platform);
        cli.execute("init");
        cli.execute("config", "user.name", "Gabriel Roldan");
        cli.execute("config", "user.email", "groldan@boundlessgeo.com");
        assertTrue(new File(workingDirectory, ".geogig").exists());
        // import with mapping
        String filename = OSMImportOp.class.getResource("nodes.xml").getFile();
        File file = new File(filename);
        String mappingFilename = OSMMap.class.getResource("nodes_mapping_with_aliases.json")
                .getFile();
        File mappingFile = new File(mappingFilename);
        cli.execute("osm", "import", file.getAbsolutePath(), "--mapping",
                mappingFile.getAbsolutePath());
        GeoGIG geogig = cli.newGeoGIG();
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:busstops/507464799").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        geogig.getRepository().workingTree().delete("node");
        Optional<RevTree> tree = geogig.command(RevObjectParse.class).setRefSpec("WORK_HEAD:node")
                .call(RevTree.class);
        assertFalse(tree.isPresent());
        geogig.close();
    }

    @Test
    public void testUnMapping() throws Exception {
        cli.execute("osm", "unmap", "busstops");
        GeoGIG geogig = cli.newGeoGIG();
        Optional<RevTree> tree = geogig.command(RevObjectParse.class).setRefSpec("HEAD:node")
                .call(RevTree.class);
        assertTrue(tree.isPresent());
        assertTrue(tree.get().size() > 0);
        Optional<RevFeature> unmapped = geogig.command(RevObjectParse.class)
                .setRefSpec("HEAD:node/507464799").call(RevFeature.class);
        assertTrue(unmapped.isPresent());
        ImmutableList<Optional<Object>> values = unmapped.get().getValues();
        assertEquals("POINT (7.1959361 50.739397)", values.get(6).get().toString());
        Map<String, String> expected = RepositoryTestCase.asMap("VRS:gemeinde", "BONN",
                "VRS:ortsteil", "Hoholz", "VRS:ref", "68566", "bus", "yes", "highway", "bus_stop",
                "name", "Gielgen", "public_transport", "platform");
        @SuppressWarnings("unchecked")
        Map<String, String> actual = (Map<String, String>) values.get(3).get();
        assertEquals(expected, actual);
        geogig.close();
    }

}
