/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import org.locationtech.geogig.storage.impl.ConflictsDatabaseConformanceTest;

public class HeapConflictsDatabaseConformanceTest
        extends ConflictsDatabaseConformanceTest<HeapConflictsDatabase> {

    @Override
    protected HeapConflictsDatabase createConflictsDatabase() throws Exception {

        return new HeapConflictsDatabase();
    }

    @Override
    protected void dispose(HeapConflictsDatabase conflicts) throws Exception {
        // nothing to do
    }

}
