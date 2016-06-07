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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

public class GeoPkgExportTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private GeogigCLI cli;

    @Override
    public void setUpInternal() throws Exception {
        Console consoleReader = new Console().disableAnsi();
        cli = new GeogigCLI(consoleReader);

        cli.setGeogig(geogig);

        // Add points
        insertAndAdd(points1);
        insertAndAdd(points2);
        insertAndAdd(points3);

        geogig.command(CommitOp.class).call();

        // Add lines
        insertAndAdd(lines1);
        insertAndAdd(lines2);
        insertAndAdd(lines3);

        geogig.command(CommitOp.class).call();
    }

    @Override
    public void tearDownInternal() throws Exception {
        cli.close();
    }

    @Test
    public void testExport() throws Exception {
        GeopkgExport exportCommand = new GeopkgExport();
        String geoPkgFileName = new File(geogig.getPlatform().pwd(), "TestPoints.gpkg")
                .getAbsolutePath();
        exportCommand.args = Arrays.asList("Points", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.run(cli);

        deleteGeoPkg(geoPkgFileName);
    }

    @Test
    public void testExportWithNullFeatureType() throws Exception {
        GeopkgExport exportCommand = new GeopkgExport();
        String geoPkgFileName = new File(geogig.getPlatform().pwd(), "TestPoints.gpkg")
                .getAbsolutePath();
        exportCommand.args = Arrays.asList(null, "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exception.expect(InvalidParameterException.class);
        exportCommand.run(cli);
    }

    @Test
    public void testExportWithInvalidFeatureType() throws Exception {
        GeopkgExport exportCommand = new GeopkgExport();
        String geoPkgFileName = new File(geogig.getPlatform().pwd(), "TestPoints.gpkg")
                .getAbsolutePath();
        exportCommand.args = Arrays.asList("invalidType", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exception.expect(InvalidParameterException.class);
        exportCommand.run(cli);
    }

    @Test
    public void testExportToFileThatAlreadyExists() throws Exception {
        GeopkgExport exportCommand = new GeopkgExport();
        String geoPkgFileName = new File(geogig.getPlatform().pwd(), "TestPoints.gpkg")
                .getAbsolutePath();

        exportCommand.args = Arrays.asList("WORK_HEAD:Points", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.run(cli);

        exportCommand.args = Arrays.asList("WORK_HEAD:Lines", "Lines");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.run(cli);

        deleteGeoPkg(geoPkgFileName);
    }

    @Test
    public void testExportWithNoArgs() throws Exception {
        GeopkgExport exportCommand = new GeopkgExport();
        exportCommand.args = Arrays.asList();
        exception.expect(CommandFailedException.class);
        exportCommand.run(cli);
    }

    @Test
    public void testExportToFileThatAlreadyExistsWithOverwrite() throws Exception {
        GeopkgExport exportCommand = new GeopkgExport();
        String geoPkgFileName = new File(geogig.getPlatform().pwd(), "TestPoints.gpkg")
                .getAbsolutePath();
        exportCommand.args = Arrays.asList("Points", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.run(cli);

        exportCommand.args = Arrays.asList("Lines", "Points");
        exportCommand.commonArgs.database = geoPkgFileName;
        exportCommand.overwrite = true;
        exportCommand.run(cli);

        deleteGeoPkg(geoPkgFileName);
    }

    private void deleteGeoPkg(String geoPkg) {
        File file = new File(geoPkg);
        if (file.exists()) {
            file.delete();
        }
    }
}
