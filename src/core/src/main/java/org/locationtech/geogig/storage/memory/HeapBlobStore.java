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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.transaction.TransactionBlobStore;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;

public class HeapBlobStore implements TransactionBlobStore {

    private ConcurrentHashMap<String, byte[]> blobs = new ConcurrentHashMap<>();

    public @Override Optional<byte[]> getBlob(String path) {
        return getBlob("", path);
    }

    public @Override Optional<InputStream> getBlobAsStream(String path) {
        return getBlobAsStream("", path);
    }

    public @Override void putBlob(String path, byte[] blob) {
        putBlob("", path, blob);
    }

    public @Override void putBlob(String path, InputStream blob) {
        putBlob("", path, blob);
    }

    public @Override void removeBlob(String path) {
        removeBlob("", path);
    }

    public @Override Optional<byte[]> getBlob(String namespace, String path) {
        return Optional.ofNullable(blobs.get(key(namespace, path)));
    }

    private String key(@Nullable String ns, String path) {
        return Strings.isNullOrEmpty(ns) ? path
                : new StringBuilder(ns).append('/').append(path).toString();
    }

    public @Override Optional<InputStream> getBlobAsStream(String namespace, String path) {
        Optional<byte[]> blob = getBlob(namespace, path);
        InputStream in = null;
        if (blob.isPresent()) {
            in = new ByteArrayInputStream(blob.get());
        }
        return Optional.ofNullable(in);
    }

    public @Override void putBlob(String namespace, String path, byte[] blob) {
        blobs.put(key(namespace, path), blob);
    }

    public @Override void putBlob(String namespace, String path, InputStream blob) {
        byte[] bytes;
        try {
            bytes = ByteStreams.toByteArray(blob);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        putBlob(namespace, path, bytes);
    }

    public @Override void removeBlob(String namespace, String path) {
        blobs.remove(key(namespace, path));
    }

    public @Override void removeBlobs(final String namespace) {
        final String prefix = namespace + "/";
        Set<String> filtered = blobs.keySet().stream().filter(key -> key.startsWith(prefix))
                .collect(Collectors.toSet());
        for (String k : filtered) {
            blobs.remove(k);
        }
    }

}
