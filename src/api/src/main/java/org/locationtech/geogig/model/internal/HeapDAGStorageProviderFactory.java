/* Copyright (c) 2019 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.model.internal;

import org.locationtech.geogig.storage.ObjectStore;

import lombok.NonNull;

public class HeapDAGStorageProviderFactory implements DAGStorageProviderFactory {

    /**
     * @return {@code 0}, lowest priority
     */
    public @Override int getPriority() {
        return 0;
    }

    public @Override DAGStorageProvider newInstance(@NonNull ObjectStore treeStore) {
        return new HeapDAGStorageProvider(treeStore);
    }
}
