/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Justin Deoliveira (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.impl.GraphDatabaseTest;

public class HeapGraphDatabaseTest extends GraphDatabaseTest {

    @Override
    protected HeapGraphDatabase createDatabase(Platform platform) {
        return new HeapGraphDatabase(platform);
    }

}
