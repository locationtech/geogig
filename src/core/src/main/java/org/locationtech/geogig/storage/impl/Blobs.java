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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.locationtech.geogig.storage.BlobStore;

import com.google.common.base.Splitter;

/**
 * Utility methods to manipulate BLOBs in a {@link BlobStore}
 */
public class Blobs {

    public static String SPARSE_FILTER_BLOB_KEY = "sparse_filter";

    public static void putBlob(BlobStore blobStore, String blobName, CharSequence contents) {
        byte[] blob = contents.toString().getBytes(StandardCharsets.UTF_8);
        blobStore.putBlob(blobName, blob);
    }

    public static Optional<String> getBlobAsString(BlobStore blobStore, String blobName) {
        Optional<byte[]> blob = getBlob(blobStore, blobName);
        Optional<String> str = Optional.empty();
        if (blob.isPresent()) {
            str = Optional.of(new String(blob.get(), StandardCharsets.UTF_8));
        }
        return str;
    }

    public static Optional<byte[]> getBlob(BlobStore blobStore, final String blobName) {
        Optional<byte[]> blob = blobStore.getBlob(blobName);
        return blob;
    }

    public static List<String> readLines(Optional<byte[]> blob) {
        List<String> lines = Collections.emptyList();
        if (blob.isPresent()) {
            String contents = new String(blob.get(), StandardCharsets.UTF_8);
            lines = Splitter.on("\n").splitToList(contents);
        }
        return lines;
    }

    public static List<String> readLines(BlobStore blobStore, String blobName) {
        Optional<byte[]> blob = getBlob(blobStore, blobName);
        return readLines(blob);
    }

}
