/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.fs;

import org.junit.Assert;
import org.junit.Test;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.impl.ConfigDatabaseTest;

public class IniFileConfigDatabaseTest extends ConfigDatabaseTest<IniFileConfigDatabase> {

    private Platform platform;

    protected IniFileConfigDatabase createDatabase(final Platform platform) {
        this.platform = platform;
        return new IniFileConfigDatabase(platform);
    }

    @Override
    protected void destroy(IniFileConfigDatabase config) {
        //
    }

    @Test
    public void testGlobalOnly() {
        ConfigDatabase globalOnly = IniFileConfigDatabase.globalOnly(platform);
        testGlobalOnly(() -> globalOnly.get("section.key"));
        testGlobalOnly(() -> globalOnly.getAll());
        testGlobalOnly(() -> globalOnly.get("section.key", String.class));
        testGlobalOnly(() -> globalOnly.getAllSection("section"));
        testGlobalOnly(() -> globalOnly.getAllSubsections("section.sub"));
        testGlobalOnly(() -> globalOnly.put("section.key", "val"));
        testGlobalOnly(() -> globalOnly.remove("section.key"));
        testGlobalOnly(() -> globalOnly.removeSection("section"));
    }

    private void testGlobalOnly(Runnable call) {
        try {
            call.run();
            Assert.fail("Expected ConfigException");
        } catch (ConfigException e) {
            Assert.assertEquals(ConfigException.StatusCode.INVALID_LOCATION, e.statusCode);
        }
    }

}
