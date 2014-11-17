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
import java.net.URL;

import jline.internal.Preconditions;

import org.locationtech.geogig.api.AbstractGeoGigOp;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

/**
 * Writes the file filter associated to an OSM import operation.
 */
public class WriteOSMFilterFile extends AbstractGeoGigOp<Void> {

    private OSMLogEntry entry;

    private String filter;

    public WriteOSMFilterFile setEntry(OSMLogEntry entry) {
        this.entry = entry;
        return this;
    }

    public WriteOSMFilterFile setFilterCode(String filter) {
        this.filter = filter;
        return this;
    }

    @Override
    protected Void _call() {
        Preconditions.checkNotNull(entry);
        Preconditions.checkNotNull(filter);
        URL url = command(ResolveOSMLogfile.class).call();
        File logfile = new File(url.getFile());
        File file = new File(logfile.getParentFile(), "filter" + entry.getId().toString());
        try {
            Files.write(filter, file, Charsets.UTF_8);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return null;
    }

}
