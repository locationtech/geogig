/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.storage.impl.TransactionBlobStore;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;

public class HeapBlobStore implements TransactionBlobStore {

    private ConcurrentHashMap<String, byte[]> blobs = new ConcurrentHashMap<>();

    @Override
    public Optional<byte[]> getBlob(String path) {
        return getBlob("", path);
    }

    @Override
    public Optional<InputStream> getBlobAsStream(String path) {
        return getBlobAsStream("", path);
    }

    @Override
    public void putBlob(String path, byte[] blob) {
        putBlob("", path, blob);
    }

    @Override
    public void putBlob(String path, InputStream blob) {
        putBlob("", path, blob);
    }

    @Override
    public void removeBlob(String path) {
        removeBlob("", path);
    }

    @Override
    public Optional<byte[]> getBlob(String namespace, String path) {
        return Optional.fromNullable(blobs.get(key(namespace, path)));
    }

    private String key(@Nullable String ns, String path) {
        return Strings.isNullOrEmpty(ns) ? path
                : new StringBuilder(ns).append('/').append(path).toString();
    }

    @Override
    public Optional<InputStream> getBlobAsStream(String namespace, String path) {
        Optional<byte[]> blob = getBlob(namespace, path);
        InputStream in = null;
        if (blob.isPresent()) {
            in = new ByteArrayInputStream(blob.get());
        }
        return Optional.fromNullable(in);
    }

    @Override
    public void putBlob(String namespace, String path, byte[] blob) {
        blobs.put(key(namespace, path), blob);
    }

    @Override
    public void putBlob(String namespace, String path, InputStream blob) {
        byte[] bytes;
        try {
            bytes = ByteStreams.toByteArray(blob);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        putBlob(namespace, path, bytes);
    }

    @Override
    public void removeBlob(String namespace, String path) {
        blobs.remove(key(namespace, path));
    }

    @Override
    public void removeBlobs(final String namespace) {
        Set<String> all = ImmutableSet.copyOf(blobs.keySet());
        Set<String> filtered = Sets.filter(all, new Predicate<String>() {
            final String prefix = namespace + "/";

            @Override
            public boolean apply(String key) {
                return key.startsWith(prefix);
            }
        });
        for (String k : filtered) {
            blobs.remove(k);
        }
    }

}
