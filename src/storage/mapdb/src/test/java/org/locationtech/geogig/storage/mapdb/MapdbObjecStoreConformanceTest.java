/* Copyright (c) 2015 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.mapdb;

import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.ObjectStoreConformanceTest;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;

public class MapdbObjecStoreConformanceTest extends ObjectStoreConformanceTest {

    @Override
    protected ObjectStore createOpen(Platform platform, Hints hints) {
        ConfigDatabase config = new IniFileConfigDatabase(platform);
        MapdbObjectDatabase store = new MapdbObjectDatabase(config, platform, hints);
        store.open();
        return store;
    }

}
