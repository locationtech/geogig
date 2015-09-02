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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.test.functional.general.CLITestContextBuilder;
import org.locationtech.geogig.osm.internal.OSMImportOp;

import com.google.common.base.Optional;

public class OSMApplyDiffTest extends Assert {

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
    public void testApplyDiff() throws Exception {
        // import and check
        GeoGIG geogig = cli.newGeoGIG();
        String filename = OSMImportOp.class.getResource("nodes_for_changeset2.xml").getFile();
        File file = new File(filename);
        cli.execute("osm", "import", file.getAbsolutePath());

        long unstaged = geogig.getRepository().workingTree().countUnstaged("node").count();
        assertTrue(unstaged > 0);
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:node/2059114068").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("WORK_HEAD:node/507464865")
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());

        String changesetFilename = OSMImportOp.class.getResource("changeset.xml").getFile();
        File changesetFile = new File(changesetFilename);
        cli.execute("osm", "apply-diff", changesetFile.getAbsolutePath());

        revFeature = geogig.command(RevObjectParse.class).setRefSpec("WORK_HEAD:node/2059114068")
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("WORK_HEAD:node/507464865")
                .call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        geogig.close();
    }

}
