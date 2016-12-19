/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Juan Marin (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.oracle;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.geotools.cli.TestHelper;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.mockito.exceptions.base.MockitoException;

public class OracleImportTest extends RepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private GeogigCLI cli;

    @Before
    public void setUpInternal() throws Exception {
        Console consoleReader = new Console().disableAnsi();
        cli = new GeogigCLI(consoleReader);

        cli.setGeogig(geogig);
    }

    @After
    public void tearDownInternal() throws Exception {
        cli.close();
    }

    @Test
    public void testImport() throws Exception {
        OracleImport importCommand = new OracleImport();
        importCommand.all = true;
        importCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        importCommand.run(cli);
    }

    @Test
    public void testNoTableNotAll() throws Exception {
        OracleImport importCommand = new OracleImport();
        importCommand.all = false;
        importCommand.table = "";
        importCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testAllAndTable() throws Exception {
        OracleImport importCommand = new OracleImport();
        importCommand.all = true;
        importCommand.table = "table1";
        importCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportTable() throws Exception {
        OracleImport importCommand = new OracleImport();
        importCommand.all = false;
        importCommand.table = "table1";
        importCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        importCommand.run(cli);
    }

    @Test
    public void testImportHelp() throws Exception {
        OracleImport importCommand = new OracleImport();
        importCommand.help = true;
        importCommand.run(cli);
    }

    @Test
    public void testInvalidDatabaseParams() throws Exception {
        OracleImport importCommand = new OracleImport();
        importCommand.commonArgs.host = "nonexistent";
        importCommand.all = true;
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportException() throws Exception {

        GeogigCLI mockCli = spy(cli);

        when(mockCli.getConsole()).thenThrow(new MockitoException("Exception"));
        OracleImport importCommand = new OracleImport();
        importCommand.all = true;
        importCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(MockitoException.class);
        importCommand.run(mockCli);
    }

    @Test
    public void testImportNonExistentTable() throws Exception {
        OracleImport importCommand = new OracleImport();
        importCommand.all = false;
        importCommand.table = "nonexistent";
        importCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testEmptyTable() throws Exception {
        OracleImport importCommand = new OracleImport();
        importCommand.all = true;
        importCommand.support.dataStoreFactory = TestHelper.createEmptyTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testNullDataStore() throws Exception {
        OracleImport importCommand = new OracleImport();
        importCommand.all = true;
        importCommand.support.dataStoreFactory = TestHelper.createNullTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportGetNamesException() throws Exception {
        OracleImport importCommand = new OracleImport();
        importCommand.all = true;
        importCommand.support.dataStoreFactory = TestHelper.createFactoryWithGetNamesException();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportFeatureSourceException() throws Exception {
        OracleImport importCommand = new OracleImport();
        importCommand.all = true;
        importCommand.support.dataStoreFactory = TestHelper
                .createFactoryWithGetFeatureSourceException();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }
}
