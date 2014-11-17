/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.osm.internal.log;

import java.io.File;
import java.io.IOException;
import java.util.List;

import jline.internal.Preconditions;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.osm.internal.Mapping;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

/**
 * Reads the mapping associated to a previously executed OSM mapping operation.
 */
public class ReadOSMMapping extends AbstractGeoGigOp<Optional<Mapping>> {

    private OSMMappingLogEntry entry;

    public ReadOSMMapping setEntry(OSMMappingLogEntry entry) {
        this.entry = entry;
        return this;
    }

    @Override
    protected Optional<Mapping> _call() {
        Preconditions.checkNotNull(entry);
        final File osmMapFolder = command(ResolveOSMMappingLogFolder.class).call();
        File file = new File(osmMapFolder, entry.getPostMappingId().toString());
        if (!file.exists()) {
            return Optional.absent();
        }
        try {
            List<String> lines = Files.readLines(file, Charsets.UTF_8);
            String s = Joiner.on("\n").join(lines);
            Mapping mapping = Mapping.fromString(s);
            return Optional.of(mapping);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

}
