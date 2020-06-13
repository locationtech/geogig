/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import org.junit.Test;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.impl.ConfigDatabaseTest;

public class HeapConfigDatabaseTest extends ConfigDatabaseTest<HeapConfigDatabase> {

    protected @Override HeapConfigDatabase createDatabase(Platform platform) {
        HeapConfigStore global = new HeapConfigStore();
        return new HeapConfigDatabase(global);
    }

    protected @Override void destroy(HeapConfigDatabase config) {
        config.close();
    }

    @Test
    public @Override void testNoUserHome() {
        // does not apply
    }

    @Test
    public @Override void testNoRepository() {
        // does not apply
    }

}
