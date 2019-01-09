/* Copyright (c) 2019 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.tempstorage.rocksdb;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.locationtech.geogig.model.ServiceFinder;
import org.locationtech.geogig.model.internal.DAGStorageProviderFactory;

public class RocksdbDAGStorageProviderFactoryTest {

    public final @Test void testServicePriority() {
        DAGStorageProviderFactory defaultService = new ServiceFinder()
                .lookupDefaultService(DAGStorageProviderFactory.class);
        assertTrue(defaultService instanceof RocksdbDAGStorageProviderFactory);
    }

}
