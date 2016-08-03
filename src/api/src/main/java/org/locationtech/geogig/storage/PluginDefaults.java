/* Copyright (c) 2014 Boundless and others.
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

public final class PluginDefaults {

    private VersionedFormat refs;

    private VersionedFormat objects;

    private VersionedFormat graph;

    PluginDefaults() {
        //
    }

    public PluginDefaults(VersionedFormat objects, VersionedFormat refs, VersionedFormat graph) {
        this.refs = refs;
        this.objects = objects;
        this.graph = graph;
    }

    public PluginDefaults(StorageProvider provider) {
        refs = provider.getRefsDatabaseFormat();
        objects = provider.getObjectDatabaseFormat();
        graph = provider.getGraphDatabaseFormat();
    }

    public Optional<VersionedFormat> getRefs() {
        return Optional.fromNullable(refs);
    }

    public Optional<VersionedFormat> getObjects() {
        return Optional.fromNullable(objects);
    }

    public Optional<VersionedFormat> getGraph() {
        return Optional.fromNullable(graph);
    }

    public void setObjects(VersionedFormat objects) {
        this.objects = objects;
    }

    public void setRefs(VersionedFormat refs) {
        this.refs = refs;
    }

    public void setGraph(VersionedFormat graph) {
        this.graph = graph;
    }

    public static final PluginDefaults NO_PLUGINS = new PluginDefaults();
}
