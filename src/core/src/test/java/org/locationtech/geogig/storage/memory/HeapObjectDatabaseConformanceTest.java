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

import org.locationtech.geogig.repository.DefaultPlatform;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.impl.ObjectDatabaseConformanceTest;

public class HeapObjectDatabaseConformanceTest extends ObjectDatabaseConformanceTest {

    @Override
    protected HeapObjectDatabase createOpen(boolean readOnly) {
        Platform platform = new DefaultPlatform();
        Hints hints = new Hints();
        hints.set(Hints.OBJECTS_READ_ONLY, readOnly);
        HeapObjectDatabase store = new HeapObjectDatabase(platform, hints);
        store.open();
        return store;
    }

}
