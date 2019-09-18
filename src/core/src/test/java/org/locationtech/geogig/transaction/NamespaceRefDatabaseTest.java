/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.transaction;

import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.memory.HeapRefDatabase;
import org.locationtech.geogig.test.integration.repository.RefDatabaseTest;

public class NamespaceRefDatabaseTest extends RefDatabaseTest {

    private String namespace = "refs/transactions/5fda5d88-9995-4d28-a54b-c57c78e86450/changed";

    private RefDatabase nonTxDatabase;

    protected @Override RefDatabase createDatabase(Platform platform) throws Exception {
        nonTxDatabase = new HeapRefDatabase();
        nonTxDatabase.open();

        NamespaceRefDatabase refDatabase = new NamespaceRefDatabase(nonTxDatabase, namespace);
        return refDatabase;
    }

}
