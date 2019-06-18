/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

/**
 * The {@code ObjectDatabase} must be per repository singleton instance providing access to the main
 * repository {@link ObjectStore} as well as extra services such as access to the conflicts and
 * blobs stores.
 * 
 * @since 1.0
 */
public interface ObjectDatabase extends ObjectStore {

    public GraphDatabase getGraphDatabase();

    /**
     * @return the {@link BlobStore} associated with this {@code ObjectDatabase}
     */
    public BlobStore getBlobStore();
}
