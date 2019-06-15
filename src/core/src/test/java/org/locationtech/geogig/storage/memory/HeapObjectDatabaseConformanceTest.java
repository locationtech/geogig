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

import java.net.URI;

import org.junit.Rule;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.impl.ObjectDatabaseConformanceTest;
import org.locationtech.geogig.test.TestRepository;

public class HeapObjectDatabaseConformanceTest extends ObjectDatabaseConformanceTest {

    private MemoryRepositoryResolver resolver = new MemoryRepositoryResolver();

    public @Rule TestRepository testSupport = new TestRepository();

    @Override
    protected ObjectDatabase createOpen(boolean readOnly) {
        URI repoURI = testSupport.getRepoURI();
        ObjectDatabase db = resolver.resolveObjectDatabase(repoURI, new Hints().readOnly(readOnly));
        db.open();
        return db;
    }

}
