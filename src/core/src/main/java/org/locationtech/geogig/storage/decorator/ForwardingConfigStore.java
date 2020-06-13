/* Copyright (c) 2020 Gabriel Roldan
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
import java.util.Map;
import java.util.Optional;

import org.locationtech.geogig.storage.ConfigStore;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public @RequiredArgsConstructor class ForwardingConfigStore implements ConfigStore {

    protected final ConfigStore actual;

    public @Override void open() {
        actual.open();
    }

    public @Override Optional<String> get(String key) {
        return actual.get(key);
    }

    public @Override void close() {
        actual.close();
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

    public @Override <T> Optional<T> get(String key, Class<T> c) {
        return actual.get(key, c);
    }

    public @Override Map<String, String> getAll() {
        return actual.getAll();
    }

    public @Override Map<String, String> getAllSection(String section) {
        return actual.getAllSection(section);
    }

    public @Override List<String> getAllSubsections(String section) {
        return actual.getAllSubsections(section);
    }

    public @Override void put(String key, Object value) {
        actual.put(key, value);
    }

    public @Override void putSection(@NonNull String section, @NonNull Map<String, String> kvp) {
        actual.putSection(section, kvp);
    }

    public @Override void remove(String key) {
        actual.remove(key);
    }

    public @Override void removeSection(String key) {
        actual.removeSection(key);
    }
}
