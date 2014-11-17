/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.repository;

import java.io.Serializable;
import java.util.Map;

import org.locationtech.geogig.api.ContextBuilder;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;

/**
 * Hints that guice created dependencies can accept on their constructors, contains flags to
 * enable/disable operational modes on databases. In the future may provide other kind of hints to
 * other components.
 * 
 * @see ContextBuilder#build(Hints)
 */
public class Hints implements Serializable {

    private static final long serialVersionUID = -1428808289446453837L;

    public static final String OBJECTS_READ_ONLY = "OBJECTS_READ_ONLY";

    public static final String STAGING_READ_ONLY = "STAGING_READ_ONLY";

    public static final String REMOTES_READ_ONLY = "REMOTES_READ_ONLY";

    private Map<String, Serializable> hintsMap = Maps.newHashMap();

    public void set(String key, Serializable value) {
        hintsMap.put(key, value);
    }

    public Optional<Serializable> get(final String key) {
        return Optional.fromNullable(hintsMap.get(key));
    }

    public boolean getBoolean(final String key) {
        return Boolean.TRUE.equals(hintsMap.get(key));
    }

    @Override
    public String toString() {
        return hintsMap.toString();
    }

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

    public static Hints readOnly() {
        Hints hints = new Hints();
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.TRUE);
        hints.set(Hints.STAGING_READ_ONLY, Boolean.TRUE);
        hints.set(Hints.REMOTES_READ_ONLY, Boolean.TRUE);
        return hints;
    }

    public static Hints readWrite() {
        Hints hints = new Hints();
        hints.set(Hints.OBJECTS_READ_ONLY, Boolean.FALSE);
        hints.set(Hints.STAGING_READ_ONLY, Boolean.FALSE);
        hints.set(Hints.REMOTES_READ_ONLY, Boolean.FALSE);
        return hints;
    }
}
