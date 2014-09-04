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
import java.util.List;

import jline.UnsupportedTerminal;
import jline.console.ConsoleReader;

import org.geotools.referencing.CRS;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry.ChangeType;
import org.locationtech.geogig.api.porcelain.DiffOp;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.test.functional.general.CLITestContextBuilder;
import org.opengis.feature.type.FeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 *
 */
public class OSMHistoryImportTest extends Assert {

    private GeogigCLI cli;

    private String fakeOsmApiUrl;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        ConsoleReader consoleReader = new ConsoleReader(System.in, System.out,
                new UnsupportedTerminal());
        cli = new GeogigCLI(consoleReader);
        fakeOsmApiUrl = getClass().getResource("../../internal/history/01_10").toExternalForm();

        File workingDirectory = tempFolder.getRoot();
        TestPlatform platform = new TestPlatform(workingDirectory);
        GlobalContextBuilder.builder = new CLITestContextBuilder(platform);
        cli.setPlatform(platform);
        cli.execute("init");
        assertTrue(new File(workingDirectory, ".geogig").exists());
    }

    @After
    public void tearDown() {
        if (cli != null) {
            cli.close();
        }
    }

    @Test
    public void test() throws Exception {
        cli.execute("config", "user.name", "Gabriel Roldan");
        cli.execute("config", "user.email", "groldan@boundlessgeo.com");
        cli.execute("osm", "import-history", fakeOsmApiUrl, "--to", "10");

        GeoGIG geogig = cli.getGeogig();
        List<DiffEntry> changes = ImmutableList.copyOf(geogig.command(DiffOp.class)
                .setOldVersion("HEAD~2").setNewVersion("HEAD~1").call());
        assertEquals(1, changes.size());
        DiffEntry entry = changes.get(0);
        assertEquals(ChangeType.MODIFIED, entry.changeType());
        assertEquals("node/20", entry.getOldObject().path());
        assertEquals("node/20", entry.getNewObject().path());

        Optional<RevFeature> oldRevFeature = geogig.command(RevObjectParse.class)
                .setObjectId(entry.getOldObject().objectId()).call(RevFeature.class);
        Optional<RevFeature> newRevFeature = geogig.command(RevObjectParse.class)
                .setObjectId(entry.getNewObject().objectId()).call(RevFeature.class);
        assertTrue(oldRevFeature.isPresent());
        assertTrue(newRevFeature.isPresent());

        Optional<RevFeatureType> type = geogig.command(RevObjectParse.class)
                .setObjectId(entry.getOldObject().getMetadataId()).call(RevFeatureType.class);
        assertTrue(type.isPresent());

        FeatureType featureType = type.get().type();

        CoordinateReferenceSystem expected = CRS.decode("EPSG:4326", true);
        CoordinateReferenceSystem actual = featureType.getCoordinateReferenceSystem();

        assertTrue(actual.toString(), CRS.equalsIgnoreMetadata(expected, actual));
    }

}
