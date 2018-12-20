/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rocksdb.performance;

import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.repository.DefaultPlatform;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.rocksdb.RocksdbObjectStore;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.test.performance.RevTreeBuilderPerformanceTest;

public class RevTreeBuilderRocksObjectsPerformanceTest extends RevTreeBuilderPerformanceTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Override
    protected ObjectStore createObjectStore() throws IOException {
        Platform platform = new DefaultPlatform();
        platform.setWorkingDir(tmp.getRoot());
        tmp.newFolder(".geogig");
        Hints hints = Hints.readWrite().platform(platform);
        try {
            hints.set(Hints.REPOSITORY_URL, tmp.getRoot().toURI().toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return new RocksdbObjectStore(platform, hints);
    }

    public static void main(String[] args) {
        RevTreeBuilderRocksObjectsPerformanceTest test = new RevTreeBuilderRocksObjectsPerformanceTest();
        try {
            test.tmp.create();
            test.before();
            test.testBuilUnordered_03_5M();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                test.after();
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.exit(0);
        }
    }
}
