/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.performance.je;

import java.io.File;

import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.bdbje.EnvironmentBuilder;
import org.locationtech.geogig.storage.bdbje.JEGraphDatabase_v0_1;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.test.TestPlatform;
import org.locationtech.geogig.test.performance.AbstractGraphDatabaseStressTest;

import com.google.common.base.Preconditions;

public class JEGraphDatabaseV1StressTest extends AbstractGraphDatabaseStressTest {
    // instance variable so its reused as if it were the singleton in the guice config
    private EnvironmentBuilder envProvider;

    @Override
    protected GraphDatabase createDatabase(TestPlatform platform) {
        File root = platform.pwd();
        Preconditions.checkState(new File(root, ".geogig").exists());

        envProvider = new EnvironmentBuilder(platform, null);

        ConfigDatabase configDB = new IniFileConfigDatabase(platform);
        return new JEGraphDatabase_v0_1(configDB, envProvider, new Hints());
    }

}
