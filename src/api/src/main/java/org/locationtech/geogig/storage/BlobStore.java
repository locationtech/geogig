/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.io.InputStream;

import com.google.common.base.Optional;

/**
 * A general purpose interface for commands to store auxiliary data
 * 
 * @since 1.0
 */
public interface BlobStore {

    /**
     * Look up a blob by key.
     * 
     * @param path the blob's name
     * @return the blob, or {@link Optional#absent()} if it was not found
     */
    public Optional<byte[]> getBlob(String path);

    /**
     * Look up a blob by key.
     * 
     * @param path the blob's name
     * @return the blob, or {@link Optional#absent()} if it was not found
     */
    public Optional<InputStream> getBlobAsStream(String path);

    /**
     * Adds a blob to the blob store.
     * 
     * @param path the blob's name
     * @param blob the blob data
     */
    public void putBlob(String path, byte[] blob);

    /**
     * Adds a blob to the blob store.
     * 
     * @param path the blob's name
     * @param blob the blob data
     */
    public void putBlob(String path, InputStream blob);

    /**
     * Removes a blob from the blob store.
     * 
     * @param path the blob's name
     */
    public void removeBlob(String path);

}