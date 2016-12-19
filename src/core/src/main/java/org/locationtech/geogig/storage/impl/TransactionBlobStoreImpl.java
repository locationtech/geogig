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
import java.util.UUID;

import org.locationtech.geogig.plumbing.TransactionBegin;
import org.locationtech.geogig.plumbing.TransactionEnd;
import org.locationtech.geogig.repository.impl.GeogigTransaction;
import org.locationtech.geogig.storage.BlobStore;

import com.google.common.base.Optional;

/**
 * A {@link BlobStore} decorator for a specific {@link GeogigTransaction transaction}.
 * 
 * @see GeogigTransaction
 * @see TransactionBegin
 * @see TransactionEnd
 */
public class TransactionBlobStoreImpl implements TransactionBlobStore {

    private final String txNamespace;

    private TransactionBlobStore store;

    /**
     * @param transactionId the transaction id
     */
    public TransactionBlobStoreImpl(final TransactionBlobStore store, final UUID transactionId) {
        this.store = store;
        this.txNamespace = transactionId.toString();
    }

    @Override
    public Optional<byte[]> getBlob(String path) {
        return getBlob(txNamespace, path);
    }

    @Override
    public Optional<InputStream> getBlobAsStream(String path) {
        return getBlobAsStream(txNamespace, path);
    }

    @Override
    public void putBlob(String path, byte[] blob) {
        putBlob(txNamespace, path, blob);
    }

    @Override
    public void putBlob(String path, InputStream blob) {
        putBlob(txNamespace, path, blob);
    }

    @Override
    public void removeBlob(String path) {
        removeBlob(txNamespace, path);
    }

    @Override
    public Optional<byte[]> getBlob(String namespace, String path) {
        return store.getBlob(namespace, path);
    }

    @Override
    public Optional<InputStream> getBlobAsStream(String namespace, String path) {
        return store.getBlobAsStream(namespace, path);
    }

    @Override
    public void putBlob(String namespace, String path, byte[] blob) {
        store.putBlob(namespace, path, blob);
    }

    @Override
    public void putBlob(String namespace, String path, InputStream blob) {
        store.putBlob(namespace, path, blob);
    }

    @Override
    public void removeBlob(String namespace, String path) {
        store.removeBlob(namespace, path);
    }

    @Override
    public void removeBlobs(String namespace) {
        store.removeBlobs(txNamespace);
    }
}
