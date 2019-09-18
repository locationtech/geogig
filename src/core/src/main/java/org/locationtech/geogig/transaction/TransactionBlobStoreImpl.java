/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.transaction;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import org.locationtech.geogig.storage.BlobStore;

/**
 * A {@link BlobStore} decorator for a specific {@link GeogigTransaction transaction}.
 * 
 * @see GeogigTransaction
 * @see TransactionBegin
 * @see TransactionEnd
 */
class TransactionBlobStoreImpl implements TransactionBlobStore {

    private final String txNamespace;

    private TransactionBlobStore store;

    /**
     * @param transactionId the transaction id
     */
    public TransactionBlobStoreImpl(final TransactionBlobStore store, final UUID transactionId) {
        this.store = store;
        this.txNamespace = transactionId.toString();
    }

    public @Override Optional<byte[]> getBlob(String path) {
        return getBlob(txNamespace, path);
    }

    public @Override Optional<InputStream> getBlobAsStream(String path) {
        return getBlobAsStream(txNamespace, path);
    }

    public @Override void putBlob(String path, byte[] blob) {
        putBlob(txNamespace, path, blob);
    }

    public @Override void putBlob(String path, InputStream blob) {
        putBlob(txNamespace, path, blob);
    }

    public @Override void removeBlob(String path) {
        removeBlob(txNamespace, path);
    }

    public @Override Optional<byte[]> getBlob(String namespace, String path) {
        return store.getBlob(namespace, path);
    }

    public @Override Optional<InputStream> getBlobAsStream(String namespace, String path) {
        return store.getBlobAsStream(namespace, path);
    }

    public @Override void putBlob(String namespace, String path, byte[] blob) {
        store.putBlob(namespace, path, blob);
    }

    public @Override void putBlob(String namespace, String path, InputStream blob) {
        store.putBlob(namespace, path, blob);
    }

    public @Override void removeBlob(String namespace, String path) {
        store.removeBlob(namespace, path);
    }

    public @Override void removeBlobs(String namespace) {
        store.removeBlobs(txNamespace);
    }
}
