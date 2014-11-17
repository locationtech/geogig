/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.sqlite;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.plumbing.ResolveGeogigDir;
import org.locationtech.geogig.api.porcelain.ConfigException;
import org.locationtech.geogig.api.porcelain.ConfigException.StatusCode;
import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.common.base.Optional;

/**
 * Base class for SQLite based config database.
 * 
 * @author Justin Deoliveira, Boundless
 * 
 */
public abstract class SQLiteConfigDatabase implements ConfigDatabase {

    final Platform platform;

    File lastWorkingDir;

    File lastUserDir;

    Config local;

    Config global;

    protected SQLiteConfigDatabase(Platform platform) {
        this.platform = platform;
    }

    @Override
    public Optional<String> get(String key) {
        return get(new Entry(key), String.class, local());
    }

    @Override
    public Optional<String> getGlobal(String key) {
        return get(new Entry(key), String.class, global());
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> c) {
        return get(new Entry(key), c, local());
    }

    @Override
    public <T> Optional<T> getGlobal(String key, Class<T> c) {
        return get(new Entry(key), c, global());
    }

    @Override
    public Map<String, String> getAll() {
        return all(local());
    }

    @Override
    public Map<String, String> getAllGlobal() {
        return all(global());
    }

    @Override
    public Map<String, String> getAllSection(String section) {
        return all(section, local());
    }

    @Override
    public Map<String, String> getAllSectionGlobal(String section) {
        return all(section, global());
    }

    @Override
    public List<String> getAllSubsections(String section) {
        return list(section, local());
    }

    @Override
    public List<String> getAllSubsectionsGlobal(String section) {
        return list(section, global());
    }

    @Override
    public void put(String key, Object value) {
        put(new Entry(key), value, local());
    }

    @Override
    public void putGlobal(String key, Object value) {
        put(new Entry(key), value, global());
    }

    @Override
    public void remove(String key) {
        remove(new Entry(key), local());
    }

    @Override
    public void removeGlobal(String key) {
        remove(new Entry(key), global());
    }

    @Override
    public void removeSection(String key) {
        removeAll(key, local());
    }

    @Override
    public void removeSectionGlobal(String key) {
        removeAll(key, global());
    }

    <T> Optional<T> get(Entry entry, Class<T> clazz, Config config) {
        String raw = get(entry, config);
        if (raw != null) {
            return Optional.of(convert(raw, clazz));
        }
        return Optional.absent();
    }

    <T> T convert(String value, Class<T> clazz) {
        // TODO: actually convert
        return clazz.cast(value);
    }

    void put(Entry entry, Object value, Config config) {
        put(entry, (String) (value != null ? value.toString() : null), config);
    }

    Config local() {
        if (local == null || !lastWorkingDir.equals(platform.pwd())) {
            final Optional<URL> url = new ResolveGeogigDir(platform).call();

            if (!url.isPresent()) {
                throw new ConfigException(StatusCode.INVALID_LOCATION);
            }

            URL u = url.get();
            File localFile;
            try {
                localFile = new File(new File(u.toURI()), "config.db");
            } catch (URISyntaxException e) {
                localFile = new File(u.getPath(), "config.db");
            }

            lastWorkingDir = platform.pwd();
            local = new Config(localFile);
        }
        return local;
    }

    Config global() {
        if (global == null || !lastUserDir.equals(platform.getUserHome())) {
            File home = platform.getUserHome();

            if (home == null) {
                throw new ConfigException(StatusCode.USERHOME_NOT_SET);
            }

            File globalDir = new File(home.getPath(), ".geogig");
            if (globalDir.exists() && !globalDir.isDirectory()) {
                throw new IllegalStateException(globalDir.getAbsolutePath()
                        + " exists but is not a directory");
            }

            if (!globalDir.exists() && !globalDir.mkdir()) {
                throw new ConfigException(StatusCode.CANNOT_WRITE);
            }

            lastUserDir = home;
            global = new Config(new File(globalDir, "config.db"));
        }
        return global;
    }

    protected static class Config {
        final File file;

        public Config(File file) {
            this.file = file;
        }
    }

    protected static class Entry {
        final String section;

        final String key;

        public Entry(String entry) {
            String[] split = entry.split("\\.");
            section = split[0];
            key = split[1];
        }
    }

    protected abstract String get(Entry entry, Config config);

    protected abstract Map<String, String> all(Config config);

    protected abstract Map<String, String> all(String section, Config config);

    protected abstract List<String> list(String section, Config config);

    protected abstract void put(Entry entry, String value, Config config);

    protected abstract void remove(Entry entry, Config config);

    protected abstract void removeAll(String section, Config config);
}
