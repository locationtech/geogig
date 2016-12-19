/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

/**
 * Hints that guice created dependencies can accept on their constructors, contains flags to
 * enable/disable operational modes on databases. In the future may provide other kind of hints to
 * other components.
 * 
 * @see ContextBuilder#build(Hints)
 * @since 1.0
 */
public class Hints implements Serializable {

    private static final long serialVersionUID = -1428808289446453837L;

    /**
     * Key for the read-only hint for the objects database.
     */
    public static final String OBJECTS_READ_ONLY = "OBJECTS_READ_ONLY";

    /**
     * Key for the read-only hint for remotes.
     */
    public static final String REMOTES_READ_ONLY = "REMOTES_READ_ONLY";

    /**
     * Key for the repository URI hint.
     */
    public static final String REPOSITORY_URL = "REPOSITORY_URL";

    /**
     * Key for the repository name hint.
     */
    public static final String REPOSITORY_NAME = "REPOSITORY_NAME";

    /**
     * Key for the platform hint.
     */
    public static final String PLATFORM = "PLATFORM";

    private Map<String, Serializable> hintsMap = Maps.newHashMap();

    /**
     * Sets a hint with the given key and value.
     * 
     * @param key the key for the hint
     * @param value the value of the hint
     */
    public void set(String key, Serializable value) {
        hintsMap.put(key, value);
    }

    /**
     * Retrieves a hint with the given key.
     * 
     * @param key the key for the hint
     * @return an {@link Optional} containing the value of the hint, or {@link Optional#absent()} if
     *         there was no hint with the given key
     */
    public Optional<Serializable> get(final String key) {
        return Optional.fromNullable(hintsMap.get(key));
    }

    /**
     * Retrieves a {@code true/false} representation of the hint with the given key.
     * 
     * @param key the key for the hint
     * @return {@code true} if the hint was found and was {@code true}
     */
    public boolean getBoolean(final String key) {
        return Boolean.TRUE.equals(hintsMap.get(key));
    }

    /**
     * @return the String representation of the hints
     */
    @Override
    public String toString() {
        return hintsMap.toString();
    }

    /**
     * Determines if this {@code Hints} object is equal to another one.
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Hints)) {
            return false;
        }
        return hintsMap.equals(((Hints) o).hintsMap);
    }

    /**
     * @return all of the hints in the form of a {@link Map}
     */
    public Map<String, Serializable> getAll() {
        return new HashMap<>(this.hintsMap);
    }

    /**
     * @return a new {@code Hints} object with the hints for a read only repository
     */
    public static Hints readOnly() {
        Hints hints = new Hints();
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.TRUE);
        hints.set(Hints.REMOTES_READ_ONLY, Boolean.TRUE);
        return hints;
    }

    /**
     * @return a new {@code Hints} object with the hints for a repository with read/write access
     */
    public static Hints readWrite() {
        Hints hints = new Hints();
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.FALSE);
        hints.set(Hints.REMOTES_READ_ONLY, Boolean.FALSE);
        return hints;
    }

    /**
     * Sets a hint for the provided repository URI.
     * 
     * @param repoURI the URI to use
     * @return {@code this}
     */
    public Hints uri(URI repoURI) {
        set(REPOSITORY_URL, repoURI);
        return this;
    }

    /**
     * Sets a hint for the provided {@link Platform}
     * 
     * @param platform the platform to set
     * @return {@code this}
     */
    public Hints platform(Platform platform) {
        set(PLATFORM, platform);
        return this;
    }
}
