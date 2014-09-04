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

import org.locationtech.geogig.api.AbstractGeoGigOp;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

/**
 * Returns the stored information about a mapping for a given tree path. Returns an absent object if
 * there is no information for the specified folder
 */
public class ReadOSMMappingLogEntry extends AbstractGeoGigOp<Optional<OSMMappingLogEntry>> {

    private String path;

    public ReadOSMMappingLogEntry setPath(String path) {
        this.path = path;
        return this;
    }

    @Override
    protected Optional<OSMMappingLogEntry> _call() {
        final File osmMapFolder = command(ResolveOSMMappingLogFolder.class).call();
        File file = new File(osmMapFolder, path);
        OSMMappingLogEntry entry = null;
        if (file.exists()) {
            try {
                synchronized (file.getCanonicalPath().intern()) {
                    String line = Files.readFirstLine(file, Charsets.UTF_8);
                    entry = OSMMappingLogEntry.fromString(line);
                }
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
        return Optional.fromNullable(entry);
    }
}
