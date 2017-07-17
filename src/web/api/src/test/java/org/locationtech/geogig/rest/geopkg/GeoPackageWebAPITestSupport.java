/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rest.geopkg;

import java.io.File;

import org.geotools.data.DataStore;
import org.geotools.data.memory.MemoryDataStore;
import org.locationtech.geogig.geotools.cli.geopkg.GeoPackageTestSupport;
import org.locationtech.geogig.test.TestData;

import com.google.common.collect.ImmutableList;

/**
 * Overrides the default GeoPackage test support class with test data from the Web API.
 */
public class GeoPackageWebAPITestSupport extends GeoPackageTestSupport {

    public GeoPackageWebAPITestSupport() {
        super();
    }

    public GeoPackageWebAPITestSupport(final File tmpFolder) {
        super(tmpFolder);
    }

    /**
     * Use web API test data.
     */
    @Override
    public File createDefaultTestData() throws Exception {

        File file = createEmptyDatabase();

        MemoryDataStore memStore = TestData.newMemoryDataStore();
        memStore.addFeatures(ImmutableList.of(TestData.point1, TestData.point2, TestData.point3));
        memStore.addFeatures(ImmutableList.of(TestData.line1, TestData.line2, TestData.line3));
        memStore.addFeatures(ImmutableList.of(TestData.poly1, TestData.poly2, TestData.poly3));

        DataStore gpkgStore = createDataStore(file);
        try {
            export(memStore.getFeatureSource(TestData.pointsType.getName().getLocalPart()),
                    gpkgStore);
            export(memStore.getFeatureSource(TestData.linesType.getName().getLocalPart()),
                    gpkgStore);
            export(memStore.getFeatureSource(TestData.polysType.getName().getLocalPart()),
                    gpkgStore);
        } finally {
            gpkgStore.dispose();
        }
        return file;
    }

}
