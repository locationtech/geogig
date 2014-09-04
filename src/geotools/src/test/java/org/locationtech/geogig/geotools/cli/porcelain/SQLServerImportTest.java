/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.porcelain;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;

import jline.UnsupportedTerminal;
import jline.console.ConsoleReader;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.mockito.exceptions.base.MockitoException;

public class SQLServerImportTest {

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
        SQLServerImport importCommand = new SQLServerImport();
        importCommand.all = true;
        importCommand.dataStoreFactory = TestHelper.createTestFactory();
        importCommand.run(cli);
    }

    @Test
    public void testNoTableNotAll() throws Exception {
        SQLServerImport importCommand = new SQLServerImport();
        importCommand.all = false;
        importCommand.table = "";
        importCommand.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testAllAndTable() throws Exception {
        SQLServerImport importCommand = new SQLServerImport();
        importCommand.all = true;
        importCommand.table = "table1";
        importCommand.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportTable() throws Exception {
        SQLServerImport importCommand = new SQLServerImport();
        importCommand.all = false;
        importCommand.table = "table1";
        importCommand.dataStoreFactory = TestHelper.createTestFactory();
        importCommand.run(cli);
    }

    @Test
    public void testImportHelp() throws Exception {
        SQLServerImport importCommand = new SQLServerImport();
        importCommand.help = true;
        importCommand.run(cli);
    }

    @Test
    public void testInvalidDatabaseParams() throws Exception {
        SQLServerImport importCommand = new SQLServerImport();
        importCommand.commonArgs.host = "nonexistent";
        importCommand.all = true;
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportException() throws Exception {
        ConsoleReader consoleReader = new ConsoleReader(System.in, System.out,
                new UnsupportedTerminal());
        GeogigCLI mockCli = spy(new GeogigCLI(consoleReader));

        setUpGeogig(mockCli);

        when(mockCli.getConsole()).thenThrow(new MockitoException("Exception"));
        SQLServerImport importCommand = new SQLServerImport();
        importCommand.all = true;
        importCommand.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(MockitoException.class);
        importCommand.run(mockCli);
    }

    @Test
    public void testImportNonExistentTable() throws Exception {
        SQLServerImport importCommand = new SQLServerImport();
        importCommand.all = false;
        importCommand.table = "nonexistent";
        importCommand.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testEmptyTable() throws Exception {
        SQLServerImport importCommand = new SQLServerImport();
        importCommand.all = true;
        importCommand.dataStoreFactory = TestHelper.createEmptyTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testNullDataStore() throws Exception {
        SQLServerImport importCommand = new SQLServerImport();
        importCommand.all = true;
        importCommand.dataStoreFactory = TestHelper.createNullTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportGetNamesException() throws Exception {
        SQLServerImport importCommand = new SQLServerImport();
        importCommand.all = true;
        importCommand.dataStoreFactory = TestHelper.createFactoryWithGetNamesException();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportFeatureSourceException() throws Exception {
        SQLServerImport importCommand = new SQLServerImport();
        importCommand.all = true;
        importCommand.dataStoreFactory = TestHelper.createFactoryWithGetFeatureSourceException();
        exception.expect(CommandFailedException.class);
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

}