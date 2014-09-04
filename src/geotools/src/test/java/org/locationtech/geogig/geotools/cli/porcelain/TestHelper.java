/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.porcelain;

import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.Serializable;

import org.geotools.data.AbstractDataStoreFactory;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

public class TestHelper {

    public static AbstractDataStoreFactory createTestFactory() throws Exception {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setCRS(CRS.decode("EPSG:4326"));
        builder.add("geom", Point.class);
        builder.add("label", String.class);
        builder.setName("table1");

        SimpleFeatureType type = builder.buildFeatureType();

        SimpleFeatureTypeBuilder builder2 = new SimpleFeatureTypeBuilder();
        builder2.setCRS(CRS.decode("EPSG:4326"));
        builder2.add("geom", Point.class);
        builder2.add("name", String.class);
        builder2.setName("table2");

        SimpleFeatureType type2 = builder2.buildFeatureType();

        SimpleFeatureTypeBuilder builder3 = new SimpleFeatureTypeBuilder();
        builder3.setCRS(CRS.decode("EPSG:4326"));
        builder3.add("geom", Point.class);
        builder3.add("name", String.class);
        builder3.add("number", Long.class);
        builder3.setName("table3");

        SimpleFeatureTypeBuilder builder4 = new SimpleFeatureTypeBuilder();
        builder4.setCRS(CRS.decode("EPSG:4326"));
        builder4.add("geom", Point.class);
        builder4.add("number", Double.class);
        builder4.setName("table4");

        // A table with a shp-like structure
        SimpleFeatureTypeBuilder builderShp = new SimpleFeatureTypeBuilder();
        builderShp.setCRS(CRS.decode("EPSG:4326"));
        builderShp.add("the_geom", Point.class);
        builderShp.add("number", Double.class);
        builderShp.add("number2", Double.class);
        builderShp.setName("shpLikeTable");

        SimpleFeatureTypeBuilder builderShp2 = new SimpleFeatureTypeBuilder();
        builderShp2.setCRS(CRS.decode("EPSG:4326"));
        builderShp2.add("the_geom", Point.class);
        builderShp2.add("number", Double.class);
        builderShp2.add("number2", Integer.class);
        builderShp2.setName("shpLikeTable2");

        // A table with a geojson-like structure
        SimpleFeatureTypeBuilder builderGeoJson = new SimpleFeatureTypeBuilder();
        builderGeoJson.setCRS(CRS.decode("EPSG:4326"));
        builderGeoJson.add("number", Double.class);
        builderGeoJson.add("number2", Double.class);
        builderGeoJson.add("geom", Point.class);
        builderGeoJson.setName("GeoJsonLikeTable");

        SimpleFeatureTypeBuilder builderGeoJson2 = new SimpleFeatureTypeBuilder();
        builderGeoJson2.setCRS(CRS.decode("EPSG:23030"));
        builderGeoJson2.add("number", Double.class);
        builderGeoJson2.add("number2", Double.class);
        builderGeoJson2.add("geom", Point.class);
        builderGeoJson2.setName("GeoJsonLikeTable2");

        SimpleFeatureType type3 = builder3.buildFeatureType();

        SimpleFeatureType typeShp = builderShp.buildFeatureType();
        SimpleFeatureType typeShp2 = builderShp2.buildFeatureType();
        SimpleFeatureType typeGeoJson = builderGeoJson.buildFeatureType();
        SimpleFeatureType typeGeoJson2 = builderGeoJson2.buildFeatureType();

        GeometryFactory gf = new GeometryFactory();
        SimpleFeature f1 = SimpleFeatureBuilder.build(type,
                new Object[] { gf.createPoint(new Coordinate(5, 8)), "feature1" },
                "table1.feature1");
        SimpleFeature f2 = SimpleFeatureBuilder.build(type,
                new Object[] { gf.createPoint(new Coordinate(5, 4)), "feature2" },
                "table1.feature2");
        SimpleFeature f3 = SimpleFeatureBuilder.build(type2,
                new Object[] { gf.createPoint(new Coordinate(3, 2)), "feature3" },
                "table2.feature3");
        SimpleFeature f4 = SimpleFeatureBuilder.build(type3,
                new Object[] { gf.createPoint(new Coordinate(0, 5)), "feature4", 1000 },
                "table2.feature4");
        SimpleFeature f5 = SimpleFeatureBuilder.build(typeShp,
                new Object[] { gf.createPoint(new Coordinate(0, 6)), 2.2, 1000 }, "feature1");
        SimpleFeature f6 = SimpleFeatureBuilder.build(typeShp2,
                new Object[] { gf.createPoint(new Coordinate(0, 7)), 3.2, 1100.0 }, "feature1");
        SimpleFeature f7 = SimpleFeatureBuilder.build(typeGeoJson,
                new Object[] { 4.2, 1200, gf.createPoint(new Coordinate(0, 8)) }, "feature1");
        SimpleFeature f8 = SimpleFeatureBuilder.build(typeGeoJson2,
                new Object[] { 4.2, 1200, gf.createPoint(new Coordinate(0, 9)) }, "feature1");

        MemoryDataStore testDataStore = new MemoryDataStore();
        testDataStore.addFeature(f1);
        testDataStore.addFeature(f2);
        testDataStore.addFeature(f3);
        testDataStore.addFeature(f4);
        testDataStore.addFeature(f5);
        testDataStore.addFeature(f6);
        testDataStore.addFeature(f7);
        testDataStore.addFeature(f8);
        testDataStore.createSchema(builder4.buildFeatureType());

        final AbstractDataStoreFactory factory = mock(AbstractDataStoreFactory.class);
        when(factory.createDataStore(anyMapOf(String.class, Serializable.class))).thenReturn(
                testDataStore);
        when(factory.canProcess(anyMapOf(String.class, Serializable.class))).thenReturn(true);

        return factory;
    }

    public static AbstractDataStoreFactory createEmptyTestFactory() throws Exception {

        MemoryDataStore testDataStore = new MemoryDataStore();

        final AbstractDataStoreFactory factory = mock(AbstractDataStoreFactory.class);
        when(factory.createDataStore(anyMapOf(String.class, Serializable.class))).thenReturn(
                testDataStore);
        when(factory.canProcess(anyMapOf(String.class, Serializable.class))).thenReturn(true);

        return factory;
    }

    public static AbstractDataStoreFactory createNullTestFactory() throws Exception {

        final AbstractDataStoreFactory factory = mock(AbstractDataStoreFactory.class);
        when(factory.createDataStore(anyMapOf(String.class, Serializable.class))).thenReturn(null);
        when(factory.canProcess(anyMapOf(String.class, Serializable.class))).thenReturn(true);

        return factory;
    }

    public static AbstractDataStoreFactory createFactoryWithGetNamesException() throws Exception {

        MemoryDataStore testDataStore = mock(MemoryDataStore.class);
        when(testDataStore.getNames()).thenThrow(new IOException());
        when(testDataStore.getTypeNames()).thenThrow(new RuntimeException());
        when(testDataStore.getSchema(anyString())).thenThrow(new IOException());

        final AbstractDataStoreFactory factory = mock(AbstractDataStoreFactory.class);
        when(factory.createDataStore(anyMapOf(String.class, Serializable.class))).thenReturn(
                testDataStore);
        when(factory.canProcess(anyMapOf(String.class, Serializable.class))).thenReturn(true);

        return factory;
    }

    public static AbstractDataStoreFactory createFactoryWithGetFeatureSourceException()
            throws Exception {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setCRS(CRS.decode("EPSG:4326"));
        builder.add("geom", Point.class);
        builder.add("label", String.class);
        builder.setName("table1");
        SimpleFeatureType type = builder.buildFeatureType();

        SimpleFeatureTypeBuilder builder2 = new SimpleFeatureTypeBuilder();
        builder2.setCRS(CRS.decode("EPSG:4326"));
        builder2.add("geom", Point.class);
        builder2.add("name", String.class);
        builder2.setName("table2");
        SimpleFeatureType type2 = builder2.buildFeatureType();

        GeometryFactory gf = new GeometryFactory();
        SimpleFeature f1 = SimpleFeatureBuilder.build(type,
                new Object[] { gf.createPoint(new Coordinate(5, 8)), "feature1" }, null);
        SimpleFeature f2 = SimpleFeatureBuilder.build(type,
                new Object[] { gf.createPoint(new Coordinate(5, 4)), "feature2" }, null);
        SimpleFeature f3 = SimpleFeatureBuilder.build(type2,
                new Object[] { gf.createPoint(new Coordinate(3, 2)), "feature3" }, null);

        MemoryDataStore testDataStore = new MemoryDataStore();
        testDataStore.addFeature(f1);
        testDataStore.addFeature(f2);
        testDataStore.addFeature(f3);

        MemoryDataStore spyDataStore = spy(testDataStore);

        when(spyDataStore.getFeatureSource("table1")).thenThrow(new IOException("Exception"));

        final AbstractDataStoreFactory factory = mock(AbstractDataStoreFactory.class);
        when(factory.createDataStore(anyMapOf(String.class, Serializable.class))).thenReturn(
                spyDataStore);
        when(factory.canProcess(anyMapOf(String.class, Serializable.class))).thenReturn(true);

        return factory;
    }
}
