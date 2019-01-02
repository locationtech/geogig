/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.tempstorage.rocksdb;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;
import org.locationtech.geogig.model.internal.CanonicalClusteringStrategyTest;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.tempstorage.rocksdb.RocksdbDAGStorageProvider;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class CanonicalClusteringStrategyRocksdbStorageTest extends CanonicalClusteringStrategyTest {

    @Override
    protected RocksdbDAGStorageProvider createStorageProvider(ObjectStore source) {
        return new RocksdbDAGStorageProvider(source);
    }
}
