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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;

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
public class PGDescribeTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private GeogigCLI cli;

    private Console consoleReader;

    @Before
    public void setUpInternal() throws Exception {
        consoleReader = spy(new Console().disableAnsi());
        cli = spy(new GeogigCLI(consoleReader));

        cli.setGeogig(geogig);
    }

    @After
    public void tearDownInternal() throws Exception {
        cli.close();
    }

    @Test
    public void testDescribe() throws Exception {
        PGDescribe describeCommand = new PGDescribe();
        describeCommand.table = "table1";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        describeCommand.run(cli);
    }

    @Test
    public void testDescribeHelp() throws Exception {
        PGDescribe describeCommand = new PGDescribe();
        describeCommand.help = true;
        describeCommand.run(cli);
    }

    @Test
    public void testInvalidDatabaseParams() throws Exception {
        PGDescribe describeCommand = new PGDescribe();
        describeCommand.commonArgs.host = "nonexistent";
        describeCommand.table = "table1";
        exception.expect(CommandFailedException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testDescribeNonexistentTable() throws Exception {
        PGDescribe describeCommand = new PGDescribe();
        describeCommand.table = "nonexistent";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testNoTable() throws Exception {
        PGDescribe describeCommand = new PGDescribe();
        describeCommand.table = "";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testNullDataStore() throws Exception {
        PGDescribe describeCommand = new PGDescribe();
        describeCommand.table = "table1";
        describeCommand.support.dataStoreFactory = TestHelper.createNullTestFactory();
        exception.expect(CommandFailedException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testDescribeException() throws Exception {
        when(cli.getConsole()).thenThrow(new MockitoException("Exception"));
        PGDescribe describeCommand = new PGDescribe();
        describeCommand.table = "table1";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(MockitoException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testFlushException() throws Exception {
        doThrow(new IOException("Exception")).when(consoleReader).flush();

        PGDescribe describeCommand = new PGDescribe();
        describeCommand.table = "table1";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(Exception.class);
        describeCommand.run(cli);
    }
}
