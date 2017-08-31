/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import com.google.common.base.Optional;

/**
 * Provides the default formats for the various databases used by GeoGig.
 *
 * @since 1.0
 */
public final class PluginDefaults {

    private VersionedFormat refs;

    private VersionedFormat objects;

    private VersionedFormat index;

    private VersionedFormat conflicts;

    /**
     * Constructs a new {@code PluginDefaults} with no configured formats or versions.
     */
    PluginDefaults() {
        //
    }

    /**
     * Constructs a new {@code PluginDefaults} with the provided formats.
     * 
     * @param objects the format and version of the {@link ObjectDatabase}
     * @param refs the format and version of the {@link RefDatabase}
     * @param index the format and version of the {@link IndexDatabase}
     */
    public PluginDefaults(VersionedFormat objects, VersionedFormat refs, VersionedFormat index,
            VersionedFormat conflicts) {
        this.refs = refs;
        this.objects = objects;
        this.index = index;
        this.conflicts = conflicts;
    }

    /**
     * Constructs a new {@code PluginDefaults} based on the provided {@link StorageProvider}.
     * 
     * @param provider the storage provider
     */
    public PluginDefaults(StorageProvider provider) {
        refs = provider.getRefsDatabaseFormat();
        objects = provider.getObjectDatabaseFormat();
        index = provider.getIndexDatabaseFormat();
        conflicts = provider.getConflictsDatabaseFormat();
    }

    /**
     * @return an {@link Optional} with the {@link RefDatabase} format and version, or
     *         {@link Optional#absent()} if there wasn't one
     */
    public Optional<VersionedFormat> getRefs() {
        return Optional.fromNullable(refs);
    }

    /**
     * @return an {@link Optional} with the {@link ObjectDatabase} format and version, or
     *         {@link Optional#absent()} if there wasn't one
     */
    public Optional<VersionedFormat> getObjects() {
        return Optional.fromNullable(objects);
    }

    /**
     * @return an {@link Optional} with the {@link IndexDatabase} format and version, or
     *         {@link Optional#absent()} if there wasn't one
     */
    public Optional<VersionedFormat> getIndex() {
        return Optional.fromNullable(index);
    }

    public Optional<VersionedFormat> getConflicts() {
        return Optional.fromNullable(conflicts);
    }

    /**
     * Sets the {@link ObjectDatabase} format and version to the provided one.
     * 
     * @param objects the format and version
     */
    public void setObjects(VersionedFormat objects) {
        this.objects = objects;
    }

    /**
     * Sets the {@link RefDatabase} format and version to the provided one.
     * 
     * @param refs the format and version
     */
    public void setRefs(VersionedFormat refs) {
        this.refs = refs;
    }

    /**
     * Sets the {@link IndexDatabase} format and version to the provided one.
     * 
     * @param index the format and version
     */
    public void setIndex(VersionedFormat index) {
        this.index = index;
    }

    public void setConflicts(VersionedFormat conflicts) {
        this.conflicts = conflicts;
    }

    /**
     * A {@code PluginDefaults} with no configured formats or versions.
     */
    public static final PluginDefaults NO_PLUGINS = new PluginDefaults();
}
