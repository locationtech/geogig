/* Copyright (c) 2015 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.performance.je;

import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.bdbje.EnvironmentBuilder;
import org.locationtech.geogig.storage.bdbje.JEObjectDatabase_v0_2;
import org.locationtech.geogig.test.performance.AbstractObjectStoreStressTest;

public class JEObjectDatabaseV2StressTest extends AbstractObjectStoreStressTest {

    @Override
    protected ObjectDatabase createDb(Platform platform, ConfigDatabase config) {

        EnvironmentBuilder envProvider = new EnvironmentBuilder(platform, null);
        JEObjectDatabase_v0_2 jedb = new JEObjectDatabase_v0_2(config, envProvider, false,
                "objects");
        return jedb;
    }

}
