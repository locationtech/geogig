/* Copyright (c) 2020 Gabriel Roldan
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage.internal;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.locationtech.geogig.storage.AbstractStore;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConfigStore;

public abstract class AbstractConfigDatabase extends AbstractStore implements ConfigDatabase {

    protected AbstractConfigDatabase() {
        this(false);
    }

    protected AbstractConfigDatabase(boolean readOnly) {
        super(readOnly);
    }

    protected abstract ConfigStore local();

    protected abstract ConfigStore global();

    public @Override Optional<String> get(String key) {
        return local().get(key);
    }

    public @Override <T> Optional<T> get(String key, Class<T> c) {
        return local().get(key, c);
    }

    public @Override Optional<String> getGlobal(String key) {
        return global().get(key);
    }

    public @Override <T> Optional<T> getGlobal(String key, Class<T> c) {
        return global().get(key, c);
    }

    public @Override Map<String, String> getAll() {
        return local().getAll();
    }

    public @Override Map<String, String> getAllGlobal() {
        return global().getAll();
    }

    public @Override Map<String, String> getAllSection(String section) {
        return local().getAllSection(section);
    }

    public @Override Map<String, String> getAllSectionGlobal(String section) {
        return global().getAllSection(section);
    }

    public @Override List<String> getAllSubsections(String section) {
        return local().getAllSubsections(section);
    }

    public @Override List<String> getAllSubsectionsGlobal(String section) {
        return global().getAllSubsections(section);
    }

    public @Override void put(String key, Object value) {
        local().put(key, value);
    }

    public @Override void putGlobal(String key, Object value) {
        global().put(key, value);
    }

    public @Override void remove(String key) {
        local().remove(key);
    }

    public @Override void removeGlobal(String key) {
        global().remove(key);
    }

    public @Override void removeSection(String key) {
        local().removeSection(key);
    }

    public @Override void removeSectionGlobal(String key) {
        global().removeSection(key);
    }
}
