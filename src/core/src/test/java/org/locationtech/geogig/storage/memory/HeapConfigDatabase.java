/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garret (Prominent Edge) - initial implementation
 * Gabriel Roldan (Boundless) - moved from api to core
 */
package org.locationtech.geogig.storage.memory;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.ConfigException.StatusCode;

import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.collect.Sets;

public class HeapConfigDatabase implements ConfigDatabase {

    private static final ConcurrentMap<String, String> global = new ConcurrentHashMap<>();

    private ConcurrentMap<String, String> local = new ConcurrentHashMap<>();

    public HeapConfigDatabase() {

    }

    @Override
    public void close() {
        local.clear();
    }

    @Override
    public Optional<String> get(String key) {
        checkKeyFormat(key);
        return fromNullable(local.get(key));
    }

    @Override
    public Optional<String> getGlobal(String key) {
        return fromNullable(global.get(key));
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> c) {
        Optional<String> val = get(key);
        return cast(c, val);
    }

    @Override
    public <T> Optional<T> getGlobal(String key, Class<T> c) {
        Optional<String> val = getGlobal(key);
        return cast(c, val);
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> cast(Class<T> c, Optional<String> s) {
        T val;
        if (!s.isPresent()) {
            val = null;
        } else if (String.class.equals(c)) {
            val = c.cast(s.get());
        } else if (int.class.equals(c) || Integer.class.equals(c)) {
            val = (T) Integer.valueOf(s.get());
        } else if (Boolean.class.equals(c)) {
            val = c.cast(Boolean.valueOf(s.get()));
        } else {
            throw new IllegalArgumentException("Unsupported type: " + c);
        }
        return fromNullable(val);
    }

    @Override
    public Map<String, String> getAll() {
        return new HashMap<>(local);
    }

    @Override
    public Map<String, String> getAllGlobal() {
        return new HashMap<>(global);
    }

    @Override
    public Map<String, String> getAllSection(String section) {
        return getAllSection(local, section);
    }

    @Override
    public Map<String, String> getAllSectionGlobal(String section) {
        return getAllSection(global, section);
    }

    private Map<String, String> getAllSection(ConcurrentMap<String, String> config,
            String section) {
        checkNotNull(section);
        CharMatcher matcher = CharMatcher.is('.');
        final int numSections = 1 + matcher.countIn(section);
        final String prefix = section + ".";

        Map<String, String> res = new HashMap<>();

        config.forEach((k, v) -> {
            if (k.startsWith(prefix) && numSections == matcher.countIn(k)) {
                res.put(k.substring(prefix.length()), v);
            }
        });

        return res;
    }

    @Override
    public List<String> getAllSubsections(String section) {
        return getAllSubSection(local, section);
    }

    @Override
    public List<String> getAllSubsectionsGlobal(String section) {
        return getAllSubSection(global, section);
    }

    private List<String> getAllSubSection(ConcurrentMap<String, String> config, String section) {
        checkNotNull(section);
        CharMatcher matcher = CharMatcher.is('.');
        final int numSections = 2 + matcher.countIn(section); // one separator for subsection and
                                                              // one for the leaf key
        final String prefix = section + ".";

        Set<String> subsections = new TreeSet<>();

        config.forEach((k, v) -> {
            if (k.startsWith(prefix) && matcher.countIn(k) >= numSections) {
                int keyIndex = k.lastIndexOf('.');
                String subsection = k.substring(prefix.length(), keyIndex);
                subsections.add(subsection);
            }
        });

        return new ArrayList<>(subsections);
    }

    @Override
    public void put(String key, Object value) {
        checkKeyFormat(key);
        if (value == null) {
            remove(key);
        } else {
            local.put(key, String.valueOf(value));
        }
    }

    @Override
    public void putGlobal(String key, Object value) {
        checkKeyFormat(key);
        if (value == null) {
            removeGlobal(key);
        } else {
            global.put(key, String.valueOf(value));
        }
    }

    @Override
    public void remove(String key) {
        checkKeyFormat(key);
        local.remove(key);
    }

    @Override
    public void removeGlobal(String key) {
        checkKeyFormat(key);
        global.remove(key);
    }

    @Override
    public void removeSection(String section) {
        removeSection(local, section);
    }

    @Override
    public void removeSectionGlobal(String section) {
        removeSection(global, section);
    }

    private void removeSection(ConcurrentMap<String, String> config, String section) {
        checkNotNull(section);
        final String prefix = section + ".";
        Set<String> matching = new HashSet<>(
                Sets.filter(config.keySet(), (k) -> k.startsWith(prefix)));
        if (matching.isEmpty()) {
            throw new ConfigException(StatusCode.MISSING_SECTION);
        }
        matching.forEach((k) -> {
            config.remove(k);
        });
    }

    private void checkKeyFormat(String qualifiedKey) {
        if (qualifiedKey == null) {
            throw new ConfigException(StatusCode.SECTION_OR_KEY_INVALID);
        }
        int firstQualifierIndex = qualifiedKey.indexOf('.');
        if (firstQualifierIndex < 1) {
            throw new ConfigException(StatusCode.SECTION_OR_NAME_NOT_PROVIDED);
        }
        if (qualifiedKey.length() == firstQualifierIndex + 1) {
            throw new ConfigException(StatusCode.SECTION_OR_NAME_NOT_PROVIDED);
        }
    }

}
