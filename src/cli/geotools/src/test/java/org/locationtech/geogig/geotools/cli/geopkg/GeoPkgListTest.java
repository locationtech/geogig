/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geopkg;

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
public class GeoPkgListTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    private GeogigCLI cli;

    private GeoPackageTestSupport support;

    @Before
    public void setUpInternal() throws Exception {
        Console consoleReader = new Console().disableAnsi();
        cli = spy(new GeogigCLI(consoleReader));

        cli.setGeogig(geogig);

        support = new GeoPackageTestSupport();
    }

    @After
    public void tearDownInternal() throws Exception {
        cli.close();
    }

    @Test
    public void testList() throws Exception {
        GeopkgList listCommand = new GeopkgList();
        listCommand.commonArgs.database = support.createDefaultTestData().getAbsolutePath();
        listCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        listCommand.run(cli);
    }

    @Test
    public void testListHelp() throws Exception {
        GeopkgList listCommand = new GeopkgList();
        listCommand.help = true;
        listCommand.run(cli);
    }

    @Test
    public void testInvalidDatabaseParams() throws Exception {
        GeopkgList listCommand = new GeopkgList();
        listCommand.commonArgs.database = "nonexistent.gpkg";
        exception.expect(IllegalArgumentException.class);
        listCommand.run(cli);
    }

    @Test
    public void testNullDataStore() throws Exception {
        GeopkgList listCommand = new GeopkgList();
        listCommand.commonArgs.database = support.newFile().getAbsolutePath();
        listCommand.support.dataStoreFactory = TestHelper.createNullTestFactory();
        exception.expect(CommandFailedException.class);
        listCommand.run(cli);
    }

    @Test
    public void testEmptyDataStore() throws Exception {
        GeopkgList listCommand = new GeopkgList();
        listCommand.commonArgs.database = support.newFile().getAbsolutePath();
        listCommand.support.dataStoreFactory = TestHelper.createEmptyTestFactory();
        exception.expect(CommandFailedException.class);
        listCommand.run(cli);
    }

    @Test
    public void testGetNamesException() throws Exception {
        GeopkgList listCommand = new GeopkgList();
        listCommand.commonArgs.database = support.newFile().getAbsolutePath();
        listCommand.support.dataStoreFactory = TestHelper.createFactoryWithGetNamesException();
        exception.expect(CommandFailedException.class);
        listCommand.run(cli);
    }

    @Test
    public void testListException() throws Exception {
        when(cli.getConsole()).thenThrow(new MockitoException("Exception"));
        GeopkgList listCommand = new GeopkgList();
        listCommand.commonArgs.database = support.newFile().getAbsolutePath();
        listCommand.support.dataStoreFactory = TestHelper.createTestFactory();
        exception.expect(MockitoException.class);
        listCommand.run(cli);
    }
}
