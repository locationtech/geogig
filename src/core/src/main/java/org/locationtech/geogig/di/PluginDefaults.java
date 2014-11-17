/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.di;

import com.google.common.base.Optional;

public final class PluginDefaults {
    private final Optional<VersionedFormat> refs, objects, staging, graph;

    public PluginDefaults() {
        refs = objects = staging = graph = Optional.absent();
    }

    public PluginDefaults(VersionedFormat objects, VersionedFormat staging, VersionedFormat refs,
            VersionedFormat graph) {
        this.refs = Optional.of(refs);
        this.objects = Optional.of(objects);
        this.staging = Optional.of(staging);
        this.graph = Optional.of(graph);
    }

    public Optional<VersionedFormat> getRefs() {
        return refs;
    }

    public Optional<VersionedFormat> getObjects() {
        return objects;
    }

    public Optional<VersionedFormat> getStaging() {
        return staging;
    }

    public Optional<VersionedFormat> getGraph() {
        return graph;
    }

    public static final PluginDefaults NO_PLUGINS = new PluginDefaults();
}
