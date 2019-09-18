/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage.decorator;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.storage.RefChange;
import org.locationtech.geogig.storage.RefDatabase;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public @RequiredArgsConstructor class ForwardingRefDatabase implements RefDatabase {

    protected final RefDatabase actual;

    public @Override void open() {
        actual.open();
    }

    public @Override void lock() throws TimeoutException {
        actual.lock();
    }

    public @Override void close() {
        actual.close();
    }

    public @Override void unlock() {
        actual.unlock();
    }

    public @Override boolean isOpen() {
        return actual.isOpen();
    }

    public @Override boolean isReadOnly() {
        return actual.isReadOnly();
    }

    public @Override void checkWritable() {
        actual.checkWritable();
    }

    public @Override void checkOpen() {
        actual.checkOpen();
    }

    public @Override Optional<Ref> get(@NonNull String name) {
        return actual.get(name);
    }

    public @Override @NonNull RefChange put(@NonNull Ref ref) {
        return actual.put(ref);
    }

    public @Override @NonNull List<RefChange> putAll(@NonNull Iterable<Ref> refs) {
        return actual.putAll(refs);
    }

    public @Override @NonNull RefChange delete(@NonNull String refName) {
        return actual.delete(refName);
    }

    public @Override @NonNull RefChange delete(@NonNull Ref ref) {
        return actual.delete(ref);
    }

    public @Override List<Ref> deleteAll(@NonNull String namespace) {
        return actual.deleteAll(namespace);
    }

    public @Override @NonNull List<Ref> getAll() {
        return actual.getAll();
    }

    public @Override @NonNull List<Ref> getAll(@NonNull String prefix) {
        return actual.getAll(prefix);
    }

    public @Override List<Ref> getAllPresent(@NonNull Iterable<String> names) {
        return actual.getAllPresent(names);
    }

    public @Override @NonNull RefChange putRef(@NonNull String name, @NonNull ObjectId value) {
        return actual.putRef(name, value);
    }

    public @Override @NonNull RefChange putSymRef(@NonNull String name, @NonNull String target) {
        return actual.putSymRef(name, target);
    }

    public @Override List<RefChange> delete(@NonNull Iterable<String> refNames) {
        return actual.delete(refNames);
    }

    public @Override @NonNull List<Ref> deleteAll() {
        return actual.deleteAll();
    }
}
