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

public class OracleDescribeTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private Console consoleReader;

    private GeogigCLI cli;

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
        OracleDescribe describeCommand = new OracleDescribe();
        describeCommand.table = "table1";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        describeCommand.run(cli);
    }

    @Test
    public void testInvalidDatabaseParams() throws Exception {
        OracleDescribe describeCommand = new OracleDescribe();
        describeCommand.commonArgs.host = "nonexistent";
        describeCommand.table = "table1";
        exception.expect(CommandFailedException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testDescribeNonexistentTable() throws Exception {
        OracleDescribe describeCommand = new OracleDescribe();
        describeCommand.table = "nonexistent";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testNoTable() throws Exception {
        OracleDescribe describeCommand = new OracleDescribe();
        describeCommand.table = "";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(CommandFailedException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testNullDataStore() throws Exception {
        OracleDescribe describeCommand = new OracleDescribe();
        describeCommand.table = "table1";
        describeCommand.support.dataStoreFactory = TestHelper.createNullTestFactory();
        exception.expect(CommandFailedException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testDescribeException() throws Exception {
        when(cli.getConsole()).thenThrow(new MockitoException("Exception"));

        OracleDescribe describeCommand = new OracleDescribe();
        describeCommand.table = "table1";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(MockitoException.class);
        describeCommand.run(cli);
    }

    @Test
    public void testFlushException() throws Exception {

        Console consoleReader = cli.getConsole();
        doThrow(new IOException("Exception")).when(consoleReader).flush();

        OracleDescribe describeCommand = new OracleDescribe();
        describeCommand.table = "table1";
        describeCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(Exception.class);
        describeCommand.run(cli);
    }
}
