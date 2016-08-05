/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.experimental.internal;

import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Preconditions;

class HeapDAGStorageProviderFactory implements DAGStorageProviderFactory {

    private final ObjectStore treeSource;

    public HeapDAGStorageProviderFactory(final ObjectStore treeSource) {
        Preconditions.checkNotNull(treeSource, "Argument treeSource is null");
        this.treeSource = treeSource;
    }

    @Override
    public DAGStorageProvider canonical() {
        return new HeapDAGStorageProvider(treeSource);
    }

    @Override
    public DAGStorageProvider quadtree() {
        return new HeapDAGStorageProvider(treeSource);
    }

}
