/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.postgis;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.geotools.TestHelper;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.mockito.exceptions.base.MockitoException;

/**
 *
 */
public class PGImportTest extends RepositoryTestCase {

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
        PGImport importCommand = new PGImport();
        importCommand.all = true;
        importCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        importCommand.run(cli);
    }

    @Test
    public void testNoTableNotAll() throws Exception {
        PGImport importCommand = new PGImport();
        importCommand.all = false;
        importCommand.table = "";
        importCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testAllAndTable() throws Exception {
        PGImport importCommand = new PGImport();
        importCommand.all = true;
        importCommand.table = "table1";
        importCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportTable() throws Exception {
        PGImport importCommand = new PGImport();
        importCommand.all = false;
        importCommand.table = "table1";
        importCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        importCommand.run(cli);
    }

    @Test
    public void testImportHelp() throws Exception {
        PGImport importCommand = new PGImport();
        importCommand.help = true;
        importCommand.run(cli);
    }

    @Test
    public void testInvalidDatabaseParams() throws Exception {
        PGImport importCommand = new PGImport();
        importCommand.commonArgs.host = "nonexistent";
        importCommand.all = true;
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportException() throws Exception {
        when(cli.getConsole()).thenThrow(new MockitoException("Exception"));
        PGImport importCommand = new PGImport();
        importCommand.all = true;
        importCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(MockitoException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportNonExistentTable() throws Exception {
        PGImport importCommand = new PGImport();
        importCommand.all = false;
        importCommand.table = "nonexistent";
        importCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testEmptyTable() throws Exception {
        PGImport importCommand = new PGImport();
        importCommand.all = true;
        importCommand.support.dataStoreFactory = TestHelper.createEmptyTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testNullDataStore() throws Exception {
        PGImport importCommand = new PGImport();
        importCommand.all = true;
        importCommand.support.dataStoreFactory = TestHelper.createNullTestFactory();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportGetNamesException() throws Exception {
        PGImport importCommand = new PGImport();
        importCommand.all = true;
        importCommand.support.dataStoreFactory = TestHelper.createFactoryWithGetNamesException();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportFeatureSourceException() throws Exception {
        PGImport importCommand = new PGImport();
        importCommand.all = true;
        importCommand.support.dataStoreFactory = TestHelper
                .createFactoryWithGetFeatureSourceException();
        exception.expect(CommandFailedException.class);
        importCommand.run(cli);
    }
}
