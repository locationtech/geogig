/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import org.locationtech.geogig.repository.DefaultPlatform;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.impl.IndexDatabaseConformanceTest;

public class HeapIndexDatabaseConformanceTest extends IndexDatabaseConformanceTest {

    @Override
    protected IndexDatabase createIndexDatabase(boolean readOnly) {
        Platform platform = new DefaultPlatform();
        Hints hints = new Hints();
        hints.set(Hints.OBJECTS_READ_ONLY, readOnly);
        HeapIndexDatabase heapIndexDatabase = new HeapIndexDatabase(platform, hints);
        return heapIndexDatabase;
    }

}
