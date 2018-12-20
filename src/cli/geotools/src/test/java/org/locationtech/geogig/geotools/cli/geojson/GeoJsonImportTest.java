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
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.plumbing.DescribeFeatureType;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.locationtech.jts.geom.LineString;
import org.mockito.exceptions.base.MockitoException;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

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
        importCommand.destTable = "importedTable";
        importCommand.run(cli);

        List<NodeRef> nodes = Lists
                .newArrayList(cli.getGeogig().getRepository().command(LsTreeOp.class)
                        .setStrategy(Strategy.DEPTHFIRST).setReference(Ref.WORK_HEAD).call());

        // There should be 3 nodes, the feature type tree and the two features.
        assertEquals(3, nodes.size());

        NodeRef rootNode = nodes.get(0);
        assertEquals("importedTable", rootNode.name());
        assertEquals(TYPE.TREE, rootNode.getType());

        NodeRef feature1 = nodes.get(1);
        NodeRef feature2 = nodes.get(2);
        ObjectId feature1id; // feature-0 id
        ObjectId feature2id; // feature-1 id
        if (feature1.name().equals("feature-0")) {
            assertEquals("feature-1", feature2.name());
            feature1id = feature1.getObjectId();
            feature2id = feature2.getObjectId();
        } else {
            assertEquals("feature-1", feature1.name());
            assertEquals("feature-0", feature2.name());
            feature1id = feature2.getObjectId();
            feature2id = feature1.getObjectId();
        }

        assertEquals(TYPE.FEATURE, feature1.getType());
        assertEquals(TYPE.FEATURE, feature2.getType());
        assertEquals("importedTable", feature1.getParentPath());
        assertEquals("importedTable", feature2.getParentPath());

        ObjectDatabase odb = cli.getGeogig().getRepository().objectDatabase();

        RevFeature feature1Obj = odb.getFeature(feature1id);
        RevFeature feature2Obj = odb.getFeature(feature2id);

        ImmutableSet<PropertyDescriptor> attributes = cli.getGeogig()
                .command(DescribeFeatureType.class)
                .setFeatureType(odb.getFeatureType(rootNode.getMetadataId())).call();
        assertEquals(3, attributes.size());
        int attributeIndex = 0;
        for (PropertyDescriptor attribute : attributes) {
            switch (attribute.getName().toString()) {
            case "geometry":
                assertTrue(LineString.class.isAssignableFrom(attribute.getType().getBinding()));
                assertEquals("LINESTRING (102 0, 103 1, 104 0, 105 1)",
                        feature1Obj.get(attributeIndex).get().toString());
                assertEquals("LINESTRING (100 0, 101 0, 101 1, 100 1)",
                        feature2Obj.get(attributeIndex).get().toString());
                break;
            case "prop0":
                assertTrue(String.class.isAssignableFrom(attribute.getType().getBinding()));
                assertEquals("value0", feature1Obj.get(attributeIndex).get());
                assertEquals("value1", feature2Obj.get(attributeIndex).get());
                break;
            case "prop1":
                assertTrue(Double.class.isAssignableFrom(attribute.getType().getBinding()));
                assertEquals(0.0, feature1Obj.get(attributeIndex).get());
                assertEquals(0.2, feature2Obj.get(attributeIndex).get());
                break;
            }
            attributeIndex++;
        }
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
