/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
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
import org.mockito.exceptions.base.MockitoException;

/**
 *
 */
public class PGListTest extends Assert {

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
    public void testList() throws Exception {
        PGList listCommand = new PGList();
        listCommand.dataStoreFactory = TestHelper.createTestFactory();
        listCommand.run(cli);
    }

    @Test
    public void testListHelp() throws Exception {
        PGList listCommand = new PGList();
        listCommand.help = true;
        listCommand.run(cli);
    }

    @Test
    public void testInvalidDatabaseParams() throws Exception {
        PGList listCommand = new PGList();
        listCommand.commonArgs.host = "nonexistent";
        exception.expect(CommandFailedException.class);
        listCommand.run(cli);
    }

    @Test
    public void testNullDataStore() throws Exception {
        PGList listCommand = new PGList();
        listCommand.dataStoreFactory = TestHelper.createNullTestFactory();
        exception.expect(CommandFailedException.class);
        listCommand.run(cli);
    }

    @Test
    public void testEmptyDataStore() throws Exception {
        PGList listCommand = new PGList();
        listCommand.dataStoreFactory = TestHelper.createEmptyTestFactory();
        listCommand.run(cli);
    }

    @Test
    public void testGetNamesException() throws Exception {
        PGList listCommand = new PGList();
        listCommand.dataStoreFactory = TestHelper.createFactoryWithGetNamesException();
        exception.expect(CommandFailedException.class);
        listCommand.run(cli);
    }

    @Test
    public void testListException() throws Exception {
        ConsoleReader consoleReader = new ConsoleReader(System.in, System.out,
                new UnsupportedTerminal());
        GeogigCLI mockCli = spy(new GeogigCLI(consoleReader));

        setUpGeogig(mockCli);

        when(mockCli.getConsole()).thenThrow(new MockitoException("Exception"));
        PGList listCommand = new PGList();
        listCommand.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(MockitoException.class);
        listCommand.run(mockCli);
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
