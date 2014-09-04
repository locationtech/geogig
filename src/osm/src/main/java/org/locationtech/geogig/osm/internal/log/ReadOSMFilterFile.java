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
import java.net.URL;
import java.util.List;

import jline.internal.Preconditions;

import org.locationtech.geogig.api.AbstractGeoGigOp;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

/**
 * Reads the file filter associated to a previously executed OSM import operation.
 */
public class ReadOSMFilterFile extends AbstractGeoGigOp<Optional<String>> {

    private OSMLogEntry entry;

    public ReadOSMFilterFile setEntry(OSMLogEntry entry) {
        this.entry = entry;
        return this;
    }

    @Override
    protected Optional<String> _call() {
        Preconditions.checkNotNull(entry);
        URL url = command(ResolveOSMLogfile.class).call();
        File logfile = new File(url.getFile());
        File file = new File(logfile.getParentFile(), "filter" + entry.getId().toString());
        if (!file.exists()) {
            return Optional.absent();
        }
        try {
            List<String> lines = Files.readLines(file, Charsets.UTF_8);
            String line = Joiner.on("\n").join(lines);
            return Optional.of(line);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

}
