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

    @Override
    protected HeapConfigDatabase createDatabase(Platform platform) {
        return new HeapConfigDatabase();
    }

    @Override
    protected void destroy(HeapConfigDatabase config) {
        config.close();
    }

    @Test
    @Override
    public void testNoUserHome() {
        // does not apply
    }

    @Test
    @Override
    public void testNoRepository() {
        // does not apply
    }

}
