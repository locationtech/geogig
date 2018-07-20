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

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.geotools.TestHelper;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.mockito.exceptions.base.MockitoException;

/**
 *
 */
public class ShpImportTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private GeogigCLI cli;

    @Before
    public void setUpInternal() throws Exception {
        Console consoleReader = new Console().disableAnsi();
        cli = spy(new GeogigCLI(consoleReader));

        cli.setGeogig(geogig);
    }

    @After
    public void tearDownInternal() throws Exception {
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
        when(cli.getConsole()).thenThrow(new MockitoException("Exception"));
        ShpImport importCommand = new ShpImport();
        importCommand.shapeFile = new ArrayList<String>();
        importCommand.shapeFile.add(ShpImport.class.getResource("shape.shp").getFile());
        exception.expect(MockitoException.class);
        importCommand.run(cli);
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
