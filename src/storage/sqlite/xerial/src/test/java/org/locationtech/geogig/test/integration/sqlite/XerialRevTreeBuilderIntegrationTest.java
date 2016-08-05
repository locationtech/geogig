/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Justin Deoliveira (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration.sqlite;

import java.io.IOException;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.test.integration.RevTreeBuilderIntegrationTest;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.storage.sqlite.XerialObjectDatabase;

import com.google.common.base.Throwables;

public class XerialRevTreeBuilderIntegrationTest extends RevTreeBuilderIntegrationTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Override
    protected ObjectStore createObjectStore() {
        Platform platform;
        try {
            platform = new TestPlatform(temp.newFolder(".geogig"));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        ConfigDatabase configDB = new IniFileConfigDatabase(platform);
        return new XerialObjectDatabase(configDB, platform, null);
    }
}
