/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.shp;

import java.io.File;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.geotools.TestHelper;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

public class ShpExportTest extends RepositoryTestCase {

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
    public void testExportWithDifferentFeatureTypes() throws Exception {
        insertAndAdd(points1B);
        geogig.command(CommitOp.class).call();
        ShpExport exportCommand = new ShpExport();
        String shapeFileName = new File(geogig.getPlatform().pwd(), "TestPoints.shp")
                .getAbsolutePath();
        exportCommand.args = Arrays.asList("Points", shapeFileName);
        exportCommand.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        exportCommand.run(cli);

        deleteShapeFile(shapeFileName);
    }

    @Test
    public void testExportNoArgs() throws Exception {
        ShpExport exportCommand = new ShpExport();
        exportCommand.args = Arrays.asList();
        exportCommand.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        exportCommand.run(cli);
    }

    @Test
    public void testExportNotEnoughArgs() throws Exception {
        ShpExport exportCommand = new ShpExport();
        exportCommand.args = Arrays.asList("TestPoints.shp");
        exportCommand.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        exportCommand.run(cli);
    }

    @Test
    public void testExport() throws Exception {
        ShpExport exportCommand = new ShpExport();
        String shapeFileName = new File(geogig.getPlatform().pwd(), "TestPoints.shp")
                .getAbsolutePath();
        exportCommand.args = Arrays.asList("Points", shapeFileName);
        exportCommand.dataStoreFactory = TestHelper.createTestFactory();
        exportCommand.run(cli);

        deleteShapeFile(shapeFileName);
    }

    @Test
    public void testExportWithNullFeatureType() throws Exception {
        ShpExport exportCommand = new ShpExport();
        String shapeFileName = new File(geogig.getPlatform().pwd(), "TestPoints.shp")
                .getAbsolutePath();
        exportCommand.args = Arrays.asList(null, shapeFileName);
        exportCommand.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(InvalidParameterException.class);
        exportCommand.run(cli);
    }

    @Test
    public void testExportWithInvalidFeatureType() throws Exception {
        ShpExport exportCommand = new ShpExport();
        String shapeFileName = new File(geogig.getPlatform().pwd(), "TestPoints.shp")
                .getAbsolutePath();
        exportCommand.args = Arrays.asList("invalidType", shapeFileName);
        exportCommand.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(InvalidParameterException.class);
        exportCommand.run(cli);
    }

    @Test
    public void testExportWithFeatureNameInsteadOfType() throws Exception {
        ShpExport exportCommand = new ShpExport();
        String shapeFileName = new File(geogig.getPlatform().pwd(), "TestPoints.shp")
                .getAbsolutePath();
        exportCommand.args = Arrays.asList("Points/Points.1", shapeFileName);
        exportCommand.dataStoreFactory = TestHelper.createTestFactory();
        try {
            exportCommand.run(cli);
            fail();
        } catch (InvalidParameterException e) {

        } finally {
            deleteShapeFile(shapeFileName);
        }
    }

    @Test
    public void testExportToFileThatAlreadyExists() throws Exception {
        ShpExport exportCommand = new ShpExport();
        String shapeFileName = new File(geogig.getPlatform().pwd(), "TestPoints.shp")
                .getAbsolutePath();
        ;
        exportCommand.args = Arrays.asList("WORK_HEAD:Points", shapeFileName);
        exportCommand.dataStoreFactory = TestHelper.createTestFactory();
        exportCommand.run(cli);

        exportCommand.args = Arrays.asList("Lines", shapeFileName);
        try {
            exportCommand.run(cli);
            fail();
        } catch (CommandFailedException e) {

        } finally {
            deleteShapeFile(shapeFileName);
        }
    }

    @Test
    public void testExportWithNoArgs() throws Exception {
        ShpExport exportCommand = new ShpExport();
        exportCommand.args = Arrays.asList();
        exportCommand.dataStoreFactory = TestHelper.createNullTestFactory();
        exception.expect(CommandFailedException.class);
        exportCommand.run(cli);
    }

    @Test
    public void testExportToFileThatAlreadyExistsWithOverwrite() throws Exception {
        ShpExport exportCommand = new ShpExport();
        String shapeFileName = new File(geogig.getPlatform().pwd(), "TestPoints.shp")
                .getAbsolutePath();
        exportCommand.args = Arrays.asList("Points", shapeFileName);
        exportCommand.dataStoreFactory = TestHelper.createTestFactory();
        exportCommand.run(cli);

        exportCommand.args = Arrays.asList("Lines", shapeFileName);
        exportCommand.overwrite = true;
        exportCommand.run(cli);

        deleteShapeFile(shapeFileName);
    }

    private void deleteShapeFile(String shapeFileName) {
        File file = new File(shapeFileName + ".shp");
        if (file.exists()) {
            file.delete();
        }
        file = new File(shapeFileName + ".fix");
        if (file.exists()) {
            file.delete();
        }
        file = new File(shapeFileName + ".shx");
        if (file.exists()) {
            file.delete();
        }
        file = new File(shapeFileName + ".qix");
        if (file.exists()) {
            file.delete();
        }
        file = new File(shapeFileName + ".prj");
        if (file.exists()) {
            file.delete();
        }
        file = new File(shapeFileName + ".dbf");
        if (file.exists()) {
            file.delete();
        }
    }

}
