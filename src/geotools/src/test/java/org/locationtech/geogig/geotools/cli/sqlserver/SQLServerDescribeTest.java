/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.sqlserver;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

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

public class SQLServerDescribeTest extends Assert {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private GeogigCLI cli;

    @Before
    public void setUp() throws Exception {
        Console consoleReader = new Console().disableAnsi();
        cli = new GeogigCLI(consoleReader);

        setUpGeogig(cli);
    }

    @After
    public void tearDown() throws Exception {
        cli.close();
    }

    @Test
    public void testDescribe() throws Exception {
        SQLServerDescribe describeCommand = new SQLServerDescribe();
        describeCommand.table = "table1";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        describeCommand.run(cli);
    }

    @Test
    public void testInvalidDatabaseParams() throws Exception {
        SQLServerDescribe describeCommand = new SQLServerDescribe();
        describeCommand.commonArgs.host = "nonexistent";
        describeCommand.table = "table1";
        exception.expect(CommandFailedException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testDescribeNonexistentTable() throws Exception {
        SQLServerDescribe describeCommand = new SQLServerDescribe();
        describeCommand.table = "nonexistent";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testNoTable() throws Exception {
        SQLServerDescribe describeCommand = new SQLServerDescribe();
        describeCommand.table = "";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testNullDataStore() throws Exception {
        SQLServerDescribe describeCommand = new SQLServerDescribe();
        describeCommand.table = "table1";
        describeCommand.support.dataStoreFactory = TestHelper.createNullTestFactory();
        exception.expect(CommandFailedException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testDescribeException() throws Exception {
        Console consoleReader = new Console().disableAnsi();
        GeogigCLI mockCli = spy(new GeogigCLI(consoleReader));

        setUpGeogig(mockCli);

        when(mockCli.getConsole()).thenThrow(new MockitoException("Exception"));
        SQLServerDescribe describeCommand = new SQLServerDescribe();
        describeCommand.table = "table1";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(MockitoException.class);
        describeCommand.run(mockCli);
    }

    @Test
    public void testFlushException() throws Exception {
        Console consoleReader = spy(new Console().disableAnsi());
        GeogigCLI testCli = new GeogigCLI(consoleReader);

        setUpGeogig(testCli);

        doThrow(new IOException("Exception")).when(consoleReader).flush();

        SQLServerDescribe describeCommand = new SQLServerDescribe();
        describeCommand.table = "table1";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(Exception.class);
        describeCommand.run(testCli);
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