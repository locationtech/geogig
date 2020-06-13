/* Copyright (c) 2020 Gabriel Roldan
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - factored out of HeapConfigDatabase
 */
package org.locationtech.geogig.storage.memory;

import static java.util.Optional.ofNullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.locationtech.geogig.storage.AbstractStore;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.ConfigException.StatusCode;
import org.locationtech.geogig.storage.ConfigStore;

import com.google.common.base.CharMatcher;
import com.google.common.collect.Sets;

import lombok.NonNull;

public class HeapConfigStore extends AbstractStore implements ConfigStore {

    private ConcurrentMap<String, String> config = new ConcurrentHashMap<>();

    public HeapConfigStore() {
        this(false);
    }

    public HeapConfigStore(boolean readOnly) {
        super(readOnly);
    }

    public void clear() {
        this.config.clear();
    }

    public @Override Optional<String> get(String key) {
        checkKeyFormat(key);
        return ofNullable(config.get(key));
    }

    public @Override <T> Optional<T> get(String key, Class<T> c) {
        Optional<String> val = get(key);
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
        return ofNullable(val);
    }

    public @Override Map<String, String> getAll() {
        return new HashMap<>(config);
    }

    public @Override Map<String, String> getAllSection(String section) {
        return getAllSection(config, section);
    }

    private Map<String, String> getAllSection(@NonNull ConcurrentMap<String, String> config,
            @NonNull String section) {
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

    public @Override List<String> getAllSubsections(String section) {
        return getAllSubSection(config, section);
    }

    private List<String> getAllSubSection(@NonNull ConcurrentMap<String, String> config,
            @NonNull String section) {
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

    public @Override void put(String key, Object value) {
        checkKeyFormat(key);
        if (value == null) {
            remove(key);
        } else {
            config.put(key, String.valueOf(value));
        }
    }

    public @Override void remove(String key) {
        checkKeyFormat(key);
        config.remove(key);
    }

    public @Override void removeSection(String section) {
        removeSection(config, section);
    }

    private void removeSection(@NonNull ConcurrentMap<String, String> config,
            @NonNull String section) {
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
            throw new ConfigException(StatusCode.SECTION_OR_KEY_INVALID);
        }
        if (qualifiedKey.length() == firstQualifierIndex + 1) {
            throw new ConfigException(StatusCode.SECTION_OR_NAME_NOT_PROVIDED);
        }
    }

}
