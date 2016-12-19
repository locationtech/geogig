/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import java.io.InputStream;

import org.locationtech.geogig.storage.BlobStore;

import com.google.common.base.Optional;

/**
 * A general purpose interface for commands to store auxiliary data
 * 
 */
public interface TransactionBlobStore extends BlobStore {

    /**
     * Look up a blob by key.
     * 
     * @param namespace the geogig transaction id
     * @param the blob's name
     * @return the blob, or {@link Optional#absent()} if it was not found
     */
    public Optional<byte[]> getBlob(String namespace, String path);

    /**
     * Look up a blob by key.
     * 
     * @param namespace the geogig transaction id
     * @param the blob's name
     * @return the blob, or {@link Optional#absent()} if it was not found
     */
    public Optional<InputStream> getBlobAsStream(String namespace, String path);

    /**
     * @param namespace the geogig transaction id Adds a blob to the blob store.
     */
    public void putBlob(String namespace, String path, byte[] blob);

    public void putBlob(String namespace, String path, InputStream blob);

    /**
     * Removes a blob from the blob store.
     */
    public void removeBlob(String namespace, String path);

    /**
     * Removes all blobs in the given transaction namespace
     */
    public void removeBlobs(String namespace);
}