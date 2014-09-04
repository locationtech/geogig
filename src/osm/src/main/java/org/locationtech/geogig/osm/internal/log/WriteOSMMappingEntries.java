/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal.log;

import java.io.File;
import java.io.IOException;

import jline.internal.Preconditions;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.osm.internal.Mapping;
import org.locationtech.geogig.osm.internal.MappingRule;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

/**
 * Writes the mapping files that store the information about a mapping operation, storing the id
 * from which the affected trees have been mapped and the mapping code used
 */
public class WriteOSMMappingEntries extends AbstractGeoGigOp<Void> {

    private Mapping mapping;

    private OSMMappingLogEntry entry;

    public WriteOSMMappingEntries setMappingLogEntry(OSMMappingLogEntry entry) {
        this.entry = entry;
        return this;
    }

    public WriteOSMMappingEntries setMapping(Mapping mapping) {
        this.mapping = mapping;
        return this;
    }

    @Override
    protected Void _call() {
        Preconditions.checkNotNull(entry);
        Preconditions.checkNotNull(mapping);
        final File osmMapFolder = command(ResolveOSMMappingLogFolder.class).call();
        for (MappingRule rule : mapping.getRules()) {
            File file = new File(osmMapFolder, rule.getName());
            try {
                Files.write(entry.toString(), file, Charsets.UTF_8);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
        File file = new File(osmMapFolder, entry.getPostMappingId().toString());
        try {
            Files.write(mapping.toString(), file, Charsets.UTF_8);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return null;
    }
}
