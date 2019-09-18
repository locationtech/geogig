package org.locationtech.geogig.dsl;

import java.util.Optional;

import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.storage.BlobStore;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public @RequiredArgsConstructor class Blobs {

    private final @NonNull Context context;

    private BlobStore store() {
        return context.blobStore();
    }

    public void remove(@NonNull String blobName) {
        store().removeBlob(blobName);
    }

    public void put(String blobName, CharSequence contents) {
        org.locationtech.geogig.storage.impl.Blobs.putBlob(store(), blobName, contents);
    }

    public Optional<String> asString(@NonNull String blobName) {
        return org.locationtech.geogig.storage.impl.Blobs.getBlobAsString(store(), blobName);
    }

    public boolean exists(@NonNull String blobName) {
        return store().getBlob(blobName).isPresent();
    }
}
