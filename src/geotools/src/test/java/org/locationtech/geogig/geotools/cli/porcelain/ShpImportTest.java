/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.geotools.cli.porcelain;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.ArrayList;

import jline.UnsupportedTerminal;
import jline.console.ConsoleReader;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.mockito.exceptions.base.MockitoException;

/**
 *
 */
public class ShpImportTest extends Assert {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private GeogigCLI cli;

    @Before
    public void setUp() throws Exception {
        ConsoleReader consoleReader = new ConsoleReader(System.in, System.out,
                new UnsupportedTerminal());
        cli = new GeogigCLI(consoleReader);

        setUpGeogig(cli);
    }

    @After
    public void tearDown() throws Exception {
        cli.close();
    }

    @Test
    public void testImport() throws Exception {
        ShpImport importCommand = new ShpImport();
        importCommand.shapeFile = new ArrayList<String>();
        importCommand.shapeFile.add(ShpImport.class.getResource("shape.shp").getFile());
        importCommand.dataStoreFactory = TestHelper.createTestFactory();
        importCommand.run(cli);
    }

    @Test
    public void testImportFileNotExist() throws Exception {
        ShpImport importCommand = new ShpImport();
        importCommand.shapeFile = new ArrayList<String>();
        importCommand.shapeFile.add("file://nonexistent.shp");
        importCommand.run(cli);
    }

    @Test
    public void testImportNullShapefileList() throws Exception {
        ShpImport importCommand = new ShpImport();
        exception.expect(InvalidParameterException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportEmptyShapefileList() throws Exception {
        ShpImport importCommand = new ShpImport();
        importCommand.shapeFile = new ArrayList<String>();
        exception.expect(InvalidParameterException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportHelp() throws Exception {
        ShpImport importCommand = new ShpImport();
        importCommand.help = true;
        importCommand.run(cli);
    }

    @Test
    public void testImportException() throws Exception {
        ConsoleReader consoleReader = new ConsoleReader(System.in, System.out,
                new UnsupportedTerminal());
        GeogigCLI mockCli = spy(new GeogigCLI(consoleReader));

        setUpGeogig(mockCli);

        when(mockCli.getConsole()).thenThrow(new MockitoException("Exception"));
        ShpImport importCommand = new ShpImport();
        importCommand.shapeFile = new ArrayList<String>();
        importCommand.shapeFile.add(ShpImport.class.getResource("shape.shp").getFile());
        exception.expect(MockitoException.class);
        importCommand.run(mockCli);
    }

    @Test
    public void testImportGetNamesException() throws Exception {
        ShpImport importCommand = new ShpImport();
        importCommand.shapeFile = new ArrayList<String>();
        importCommand.shapeFile.add(ShpImport.class.getResource("shape.shp").getFile());
        importCommand.dataStoreFactory = TestHelper.createFactoryWithGetNamesException();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportFeatureSourceException() throws Exception {
        ShpImport importCommand = new ShpImport();
        importCommand.shapeFile = new ArrayList<String>();
        importCommand.shapeFile.add(ShpImport.class.getResource("shape.shp").getFile());
        importCommand.dataStoreFactory = TestHelper.createFactoryWithGetFeatureSourceException();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testNullDataStore() throws Exception {
        ShpImport importCommand = new ShpImport();
        importCommand.shapeFile = new ArrayList<String>();
        importCommand.shapeFile.add(ShpImport.class.getResource("shape.shp").getFile());
        importCommand.dataStoreFactory = TestHelper.createNullTestFactory();
        importCommand.run(cli);
    }

    private void setUpGeogig(GeogigCLI cli) throws Exception {
        final File userhome = tempFolder.newFolder("mockUserHomeDir");
        final File workingDir = tempFolder.newFolder("mockWorkingDir");
        tempFolder.newFolder("mockWorkingDir/.geogig");

        final Platform platform = mock(Platform.class);
        when(platform.pwd()).thenReturn(workingDir);
        when(platform.getUserHome()).thenReturn(userhome);

        cli.setPlatform(platform);
    }

    public void testImportWithFidAttribute() throws Exception {
        ShpImport importCommand = new ShpImport();
        importCommand.shapeFile = new ArrayList<String>();
        importCommand.shapeFile.add(ShpImport.class.getResource("shape.shp").getFile());
        importCommand.dataStoreFactory = TestHelper.createTestFactory();
        importCommand.fidAttribute = "label";
        importCommand.run(cli);
    }

    @Test
    public void testImportWithWrongFidAttribute() throws Exception {
        ShpImport importCommand = new ShpImport();
        importCommand.shapeFile = new ArrayList<String>();
        importCommand.shapeFile.add(ShpImport.class.getResource("shape.shp").getFile());
        importCommand.dataStoreFactory = TestHelper.createTestFactory();
        importCommand.fidAttribute = "wrong";
        exception.expect(InvalidParameterException.class);
        importCommand.run(cli);
    }

}
