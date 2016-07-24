/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.bdbje;

import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStoreConformanceTest;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;

public class JEObjectStoreConformanceTest extends ObjectStoreConformanceTest {

    @Override
    protected ObjectDatabase createOpen(Platform platform, Hints hints) {
        EnvironmentBuilder envProvider;
        envProvider = new EnvironmentBuilder(platform, null);
        ConfigDatabase configDB = new IniFileConfigDatabase(platform);
        JEObjectDatabase db = new JEObjectDatabase_v0_1(configDB, envProvider, hints);
        db.open();
        return db;
    }
}
