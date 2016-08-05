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

class CachingDAGStorageProviderFactory implements DAGStorageProviderFactory {

    private final ObjectStore source;

    CachingDAGStorageProviderFactory(final ObjectStore source) {
        this.source = source;
    }

    @Override
    public DAGStorageProvider canonical() {
        return new CachingDAGStorageProvider(source);
    }

    @Override
    public DAGStorageProvider quadtree() {
        throw new UnsupportedOperationException();
    }

}
