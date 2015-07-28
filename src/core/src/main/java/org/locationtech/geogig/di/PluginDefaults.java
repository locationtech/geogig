/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.di;

import com.google.common.base.Optional;

public final class PluginDefaults {
    private Optional<VersionedFormat> refs, objects, graph;

    public PluginDefaults() {
        refs = objects = graph = Optional.absent();
    }

    public PluginDefaults(VersionedFormat objects, VersionedFormat refs, VersionedFormat graph) {
        this.refs = Optional.of(refs);
        this.objects = Optional.of(objects);
        this.graph = Optional.of(graph);
    }

    public Optional<VersionedFormat> getRefs() {
        return refs;
    }

    public Optional<VersionedFormat> getObjects() {
        return objects;
    }

    public Optional<VersionedFormat> getGraph() {
        return graph;
    }

    public void setObjects(VersionedFormat objects) {
        this.objects = Optional.fromNullable(objects);
    }

    public void setRefs(VersionedFormat refs) {
        this.refs = Optional.fromNullable(refs);
    }

    public void setGraph(VersionedFormat graph) {
        this.graph = Optional.fromNullable(graph);
    }

    public static final PluginDefaults NO_PLUGINS = new PluginDefaults();
}
