/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Juan Marin (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geojson;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.mockito.exceptions.base.MockitoException;

public class GeoJsonImportTest extends RepositoryTestCase {

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
        GeoJsonImport importCommand = new GeoJsonImport();
        importCommand.geoJSONList = new ArrayList<String>();
        importCommand.geoJSONList.add(GeoJsonImport.class.getResource("sample.geojson").getFile());
        importCommand.run(cli);
    }

    @Test
    public void testImportFileNotExist() throws Exception {
        GeoJsonImport importCommand = new GeoJsonImport();
        importCommand.geoJSONList = new ArrayList<String>();
        importCommand.geoJSONList.add("file://nonexistent.geojson");
        importCommand.run(cli);
    }

    @Test
    public void testImportNullGeoJSONList() throws Exception {
        GeoJsonImport importCommand = new GeoJsonImport();
        exception.expect(InvalidParameterException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportEmptyGeoJSONList() throws Exception {
        GeoJsonImport importCommand = new GeoJsonImport();
        importCommand.geoJSONList = new ArrayList<String>();
        exception.expect(InvalidParameterException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportHelp() throws Exception {
        GeoJsonImport importCommand = new GeoJsonImport();
        importCommand.help = true;
        importCommand.run(cli);
    }

    @Test
    public void testImportGeomNameAndGeomNameAuto() throws Exception {
        GeoJsonImport importCommand = new GeoJsonImport();
        importCommand.geoJSONList = new ArrayList<String>();
        importCommand.geoJSONList.add(GeoJsonImport.class.getResource("sample.geojson").getFile());
        importCommand.geomName = "the_geom";
        importCommand.geomNameAuto = true;
        exception.expect(InvalidParameterException.class);
        importCommand.run(cli);
    }

    @Test
    public void testImportException() throws Exception {
        when(cli.getConsole()).thenThrow(new MockitoException("Exception"));
        GeoJsonImport importCommand = new GeoJsonImport();
        importCommand.geoJSONList = new ArrayList<String>();
        importCommand.geoJSONList.add(GeoJsonImport.class.getResource("sample.geojson").getFile());
        exception.expect(MockitoException.class);
        importCommand.run(cli);
    }
}
