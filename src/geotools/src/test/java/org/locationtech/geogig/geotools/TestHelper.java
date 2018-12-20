/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools;

import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.Serializable;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.data.DataUtilities;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.feature.NameImpl;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.mockito.Mockito;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

public class TestHelper {

    private static SimpleFeatureType type(String typeName, String typeSpec) throws SchemaException {
        // Use the same deafult namespace than SimpleFeatureTypeBuilder
        typeName = "http://www.opengis.net/gml." + typeName;
        SimpleFeatureType type = DataUtilities.createType(typeName, typeSpec);
        return type;
    }

    private static SimpleFeature feature(SimpleFeatureType type, String id, Object... values) {
        return SimpleFeatureBuilder.build(type, values, id);
    }

    private static Geometry geom(String wkt) throws ParseException {
        return new WKTReader().read(wkt);
    }

    public static DataStoreFactorySpi createTestFactory() throws Exception {

        final SimpleFeatureType type1;
        final SimpleFeatureType type2;
        final SimpleFeatureType type3;
        final SimpleFeatureType type4;

        final SimpleFeatureType typeShp;
        final SimpleFeatureType typeShp2;
        final SimpleFeatureType typeGeoJson;
        final SimpleFeatureType typeGeoJson2;

        type1 = type("table1", "geom:Point:srid=4326,label:String");
        type2 = type("table2", "geom:Point:srid=4326,name:String");
        type3 = type("table3", "geom:Point:srid=4326,name:String,number:java.lang.Long");
        type4 = type("table4", "geom:Point:srid=4326,number:java.lang.Double");

        // A table with a shp-like structure
        typeShp = type("shpLikeTable",
                "the_geom:Point:srid=4326,number:java.lang.Double,number2:java.lang.Double");
        typeShp2 = type("shpLikeTable2",
                "the_geom:Point:srid=4326,number:java.lang.Double,number2:java.lang.Integer");

        // A table with a geojson-like structure
        typeGeoJson = type("GeoJsonLikeTable",
                "number:java.lang.Double,number2:java.lang.Double,geom:Point:srid=4326");

        typeGeoJson2 = type("GeoJsonLikeTable2",
                "number:java.lang.Double,number2:java.lang.Double,geom:Point:srid=23030");

        final SimpleFeature f1 = feature(type1, "table1.feature1", geom("POINT(5 8)"), "feature1");
        final SimpleFeature f2 = feature(type1, "table1.feature2", geom("POINT(5 4)"), "feature2");
        final SimpleFeature f3 = feature(type2, "table2.feature3", geom("POINT(3 2)"), "feature3");
        final SimpleFeature f4 = feature(type3, "table2.feature4", geom("POINT(0 5)"), "feature4",
                1000);
        final SimpleFeature f5 = feature(typeShp, "feature1", geom("POINT(0 6)"), 2.2, 1000d);
        final SimpleFeature f6 = feature(typeShp2, "feature1", geom("POINT(0 7)"), 3.2, 1100);
        final SimpleFeature f7 = feature(typeGeoJson, "feature1", 4.2d, 1200d, geom("POINT(0 8)"));
        final SimpleFeature f8 = feature(typeGeoJson2, "feature1", 4.2d, 1200d, geom("POINT(0 9)"));

        MemoryDataStore testDataStore = new MemoryDataStore();
        testDataStore.createSchema(type1);
        testDataStore.createSchema(type2);
        testDataStore.createSchema(type3);
        testDataStore.createSchema(type4);
        testDataStore.createSchema(typeShp);
        testDataStore.createSchema(typeShp2);
        testDataStore.createSchema(typeGeoJson);
        testDataStore.createSchema(typeGeoJson2);

        testDataStore.addFeatures(new SimpleFeature[] { f1, f2, f3, f4, f5, f6, f7, f8 });

        final DataStoreFactorySpi factory = mock(DataStoreFactorySpi.class);
        when(factory.createDataStore(anyMapOf(String.class, Serializable.class)))
                .thenReturn(testDataStore);
        when(factory.canProcess(anyMapOf(String.class, Serializable.class))).thenReturn(true);

        return factory;
    }

    public static DataStoreFactorySpi createEmptyTestFactory() throws Exception {

        MemoryDataStore testDataStore = new MemoryDataStore();

        final DataStoreFactorySpi factory = mock(DataStoreFactorySpi.class);
        when(factory.createDataStore(anyMapOf(String.class, Serializable.class)))
                .thenReturn(testDataStore);
        when(factory.canProcess(anyMapOf(String.class, Serializable.class))).thenReturn(true);

        return factory;
    }

    public static DataStoreFactorySpi createNullTestFactory() throws Exception {

        final DataStoreFactorySpi factory = mock(DataStoreFactorySpi.class);
        when(factory.createDataStore(anyMapOf(String.class, Serializable.class))).thenReturn(null);
        when(factory.canProcess(anyMapOf(String.class, Serializable.class))).thenReturn(true);

        return factory;
    }

    public static DataStoreFactorySpi createFactoryWithGetNamesException() throws Exception {

        DataStore testDataStore = mock(DataStore.class);
        when(testDataStore.getNames()).thenThrow(new IOException());
        when(testDataStore.getTypeNames()).thenThrow(new RuntimeException());
        when(testDataStore.getSchema(anyString())).thenThrow(new IOException());

        final DataStoreFactorySpi factory = mock(DataStoreFactorySpi.class);
        when(factory.createDataStore(anyMapOf(String.class, Serializable.class)))
                .thenReturn(testDataStore);
        when(factory.canProcess(anyMapOf(String.class, Serializable.class))).thenReturn(true);

        return factory;
    }

    public static DataStoreFactorySpi createFactoryWithGetFeatureSourceException()
            throws Exception {

        final SimpleFeatureType type = type("table1", "geom:Point:srid=4326,label:String");
        final SimpleFeatureType type2 = type("table2", "geom:Point:srid=4326,name:String");

        final SimpleFeature f1 = feature(type, null, geom("POINT(5 8)"), "feature1");
        final SimpleFeature f2 = feature(type, null, geom("POINT(5 4)"), "feature2");
        final SimpleFeature f3 = feature(type2, null, geom("POINT(3 2)"), "feature3");

        MemoryDataStore testDataStore = new MemoryDataStore();
        testDataStore.addFeature(f1);
        testDataStore.addFeature(f2);
        testDataStore.addFeature(f3);

        MemoryDataStore spyDataStore = spy(testDataStore);

        Mockito.doThrow(new IOException("Exception")).when(spyDataStore)
                .getFeatureSource(eq("table1"));
        Mockito.doThrow(new IOException("Exception")).when(spyDataStore)
                .getFeatureSource(eq(new NameImpl("table1")));

        final DataStoreFactorySpi factory = mock(DataStoreFactorySpi.class);
        when(factory.createDataStore(anyMapOf(String.class, Serializable.class)))
                .thenReturn(spyDataStore);
        when(factory.canProcess(anyMapOf(String.class, Serializable.class))).thenReturn(true);

        return factory;
    }
}
