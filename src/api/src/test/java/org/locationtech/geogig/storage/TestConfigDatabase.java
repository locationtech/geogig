/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.base.Optional;

public class TestConfigDatabase implements ConfigDatabase {
    private Map<String, String> localConfig = new HashMap<String, String>();

    private Map<String, String> globalConfig = new HashMap<String, String>();

    @Override
    public void close() throws IOException {
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.fromNullable(localConfig.get(key));
    }

    @Override
    public Optional<String> getGlobal(String key) {
        return Optional.fromNullable(globalConfig.get(key));
    }

    private <T> Optional<T> getInternal(String key, Class<T> c, Optional<String> text) {
        if (text.isPresent()) {
            return Optional.of(cast(c, text.get()));
        } else {
            return Optional.absent();
        }
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> c) {
        Optional<String> text = get(key);
        return getInternal(key, c, text);
    }

    @Override
    public <T> Optional<T> getGlobal(String key, Class<T> c) {
        Optional<String> text = getGlobal(key);
        return getInternal(key, c, text);
    }

    @Override
    public Map<String, String> getAll() {
        return localConfig;
    }

    @Override
    public Map<String, String> getAllGlobal() {
        return globalConfig;
    }

    private Map<String, String> getAllSectionInternal(String section, Map<String, String> config) {
        Map<String, String> sectionMap = new HashMap<String, String>();
        for (Entry<String, String> entry : config.entrySet()) {
            if (entry.getKey().startsWith(section + ".")) {
                sectionMap.put(entry.getKey(), entry.getValue());
            }
        }
        return sectionMap;
    }

    @Override
    public Map<String, String> getAllSection(String section) {
        return getAllSectionInternal(section, localConfig);
    }

    @Override
    public Map<String, String> getAllSectionGlobal(String section) {
        return getAllSectionInternal(section, globalConfig);
    }

    private List<String> getAllSubsectionsInternal(String section, Map<String, String> config) {
        List<String> subsections = new LinkedList<String>();
        String sectionPrefix = section + ".";
        for (Entry<String, String> entry : config.entrySet()) {
            if (entry.getKey().startsWith(sectionPrefix)) {
                String[] tokens = entry.getKey().replace(sectionPrefix, "").split("\\.");
                if (tokens.length > 1) {
                    subsections.add(tokens[0]);
                }
            }
        }
        return subsections;
    }

    @Override
    public List<String> getAllSubsections(String section) {
        return getAllSubsectionsInternal(section, localConfig);
    }

    @Override
    public List<String> getAllSubsectionsGlobal(String section) {
        return getAllSubsectionsInternal(section, globalConfig);
    }

    @Override
    public void put(String key, Object value) {
        localConfig.put(key, value.toString());
    }

    @Override
    public void putGlobal(String key, Object value) {
        globalConfig.put(key, value.toString());
    }

    @Override
    public void remove(String key) {
        localConfig.remove(key);
    }

    @Override
    public void removeGlobal(String key) {
        globalConfig.remove(key);
    }

    private void removeSectionInternal(String key, Map<String, String> config) {
        List<String> keysToRemove = new LinkedList<String>();
        for (Entry<String, String> entry : config.entrySet()) {
            if (entry.getKey().startsWith(key + ".")) {
                keysToRemove.add(entry.getKey());
            }
        }
        for (String keyToRemove : keysToRemove) {
            config.remove(keyToRemove);
        }
    }

    @Override
    public void removeSection(String key) {
        removeSectionInternal(key, localConfig);
    }

    @Override
    public void removeSectionGlobal(String key) {
        removeSectionInternal(key, globalConfig);
    }

    @SuppressWarnings("unchecked")
    private <T> T cast(Class<T> c, String s) {
        if (String.class.equals(c)) {
            return c.cast(s);
        }
        if (int.class.equals(c) || Integer.class.equals(c)) {
            return (T) Integer.valueOf(s);
        }
        if (Boolean.class.equals(c)) {
            return c.cast(Boolean.valueOf(s));
        }
        throw new IllegalArgumentException("Unsupported type: " + c);
    }

}
