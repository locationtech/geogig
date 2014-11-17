/*******************************************************************************
 * Copyright (c) 2012, 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.memory;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.storage.AbstractRefDatabase;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * Provides an implementation of a GeoGig ref database that utilizes the heap for the storage of
 * refs.
 */
public class HeapRefDatabase extends AbstractRefDatabase {

    private Map<String, String> refs;

    /**
     * Creates the reference database.
     */
    @Override
    public void create() {
        if (refs == null) {
            refs = Maps.newTreeMap();
        }
    }

    /**
     * Closes the reference database.
     */
    @Override
    public void close() {
        if (refs != null) {
            refs.clear();
            refs = null;
        }
    }

    /**
     * @param name the name of the ref (e.g. {@code "refs/remotes/origin"}, etc).
     * @return the ref, or {@code null} if it doesn't exist
     */
    @Override
    public String getRef(String name) {
        String val = refs.get(name);
        if (val == null) {
            return null;
        }
        try {
            ObjectId.valueOf(val);
        } catch (IllegalArgumentException e) {
            throw e;
        }
        return val;
    }

    /**
     * @param name the name of the ref
     * @param value the value of the ref
     * @return {@code null} if the ref didn't exist already, its old value otherwise
     */
    @Override
    public void putRef(String name, String value) {
        checkNotNull(name);
        checkNotNull(value);
        ObjectId.valueOf(value);
        refs.put(name, value);
    }

    /**
     * @param refName the name of the ref to remove (e.g. {@code "HEAD"},
     *        {@code "refs/remotes/origin"}, etc).
     * @return the value of the ref before removing it, or {@code null} if it didn't exist
     */
    @Override
    public String remove(String refName) {
        checkNotNull(refName);
        String oldValue = refs.remove(refName);
        return oldValue;
    }

    /**
     * @param name the name of the symbolic ref (e.g. {@code "HEAD"}, etc).
     * @return the ref, or {@code null} if it doesn't exist
     */
    @Override
    public String getSymRef(String name) {
        checkNotNull(name);
        String value = refs.get(name);
        if (value == null) {
            return null;
        }
        if (!value.startsWith("ref: ")) {
            throw new IllegalArgumentException(name + " is not a symbolic ref: '" + value + "'");
        }
        return value.substring("ref: ".length());
    }

    /**
     * @param name the name of the symbolic ref
     * @param val the value of the symbolic ref
     * @return {@code null} if the ref didn't exist already, its old value otherwise
     */
    @Override
    public void putSymRef(String name, String val) {
        checkNotNull(name);
        checkNotNull(val);
        val = "ref: " + val;
        refs.put(name, val);
    }

    /**
     * @return all known references under the "refs" namespace (i.e. not top level ones like HEAD,
     *         etc), key'ed by ref name
     */
    @Override
    public Map<String, String> getAll() {

        Predicate<String> keyPredicate = new Predicate<String>() {

            @Override
            public boolean apply(String refName) {
                return refName.startsWith("refs/");
            }
        };
        return Maps.filterKeys(ImmutableMap.copyOf(this.refs), keyPredicate);
    }

    @Override
    public Map<String, String> getAll(final String prefix) {

        Predicate<String> keyPredicate = new Predicate<String>() {

            @Override
            public boolean apply(String refName) {
                return refName.startsWith(prefix);
            }
        };
        return Maps.filterKeys(ImmutableMap.copyOf(this.refs), keyPredicate);
    }

    @Override
    public Map<String, String> removeAll(final String namespace) {

        Predicate<String> keyPredicate = new Predicate<String>() {

            @Override
            public boolean apply(String refName) {
                return refName.startsWith(namespace);
            }
        };
        Map<String, String> removed = Maps.filterKeys(ImmutableMap.copyOf(this.refs), keyPredicate);
        for (String key : removed.keySet()) {
            refs.remove(key);
        }
        return null;
    }

    @Override
    public void configure() {
        // No-op
    }
    
    @Override
    public void checkConfig() {
        // No-op
    }
}
