/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Juan Marin (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.oracle;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.geotools.cli.TestHelper;
import org.mockito.exceptions.base.MockitoException;

public class OracleListTest extends Assert {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private GeogigCLI cli;

    @Before
    public void setUp() throws Exception {
        Console consoleReader = new Console().disableAnsi();
        cli = spy(new GeogigCLI(consoleReader));

        setUpGeogig(cli);
    }

    @After
    public void tearDown() throws Exception {
        cli.close();
    }

    @Test
    public void testList() throws Exception {
        OracleList listCommand = new OracleList();
        listCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        listCommand.run(cli);
    }

    @Test
    public void testListHelp() throws Exception {
        OracleList listCommand = new OracleList();
        listCommand.help = true;
        listCommand.run(cli);
    }

    @Test
    public void testInvalidDatabaseParams() throws Exception {
        OracleList listCommand = new OracleList();
        listCommand.commonArgs.host = "nonexistent";
        exception.expect(CommandFailedException.class);
        listCommand.run(cli);
    }

    @Test
    public void testNullDataStore() throws Exception {
        OracleList listCommand = new OracleList();
        listCommand.support.dataStoreFactory = TestHelper.createNullTestFactory();
        exception.expect(CommandFailedException.class);
        listCommand.run(cli);
    }

    @Test
    public void testEmptyDataStore() throws Exception {
        OracleList listCommand = new OracleList();
        listCommand.support.dataStoreFactory = TestHelper.createEmptyTestFactory();
        exception.expect(CommandFailedException.class);
        listCommand.run(cli);
    }

    @Test
    public void testGetNamesException() throws Exception {
        OracleList listCommand = new OracleList();
        listCommand.support.dataStoreFactory = TestHelper.createFactoryWithGetNamesException();
        exception.expect(CommandFailedException.class);
        listCommand.run(cli);
    }

    @Test
    public void testListException() throws Exception {
        when(cli.getConsole()).thenThrow(new MockitoException("Exception"));
        OracleList listCommand = new OracleList();
        listCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(MockitoException.class);
        listCommand.run(cli);
    }

    private void setUpGeogig(GeogigCLI cli) throws Exception {
        final File userhome = tempFolder.newFolder("mockUserHomeDir");
        final File workingDir = tempFolder.newFolder("mockWorkingDir");
        tempFolder.newFolder("mockWorkingDir", ".geogig");

        final Platform platform = mock(Platform.class);
        when(platform.pwd()).thenReturn(workingDir);
        when(platform.getUserHome()).thenReturn(userhome);

        cli.setPlatform(platform);
    }

}
