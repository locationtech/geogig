/* Copyright (c) 2015 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.performance.mapdb;

import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectDatabaseStressTest;
import org.locationtech.geogig.storage.mapdb.MapdbObjectDatabase;

public class MapdbObjectDatabaseStressTest extends ObjectDatabaseStressTest {

    @Override
    protected MapdbObjectDatabase createDb(Platform platform, ConfigDatabase config) {

        return new MapdbObjectDatabase(config, platform, null);
    }

}
