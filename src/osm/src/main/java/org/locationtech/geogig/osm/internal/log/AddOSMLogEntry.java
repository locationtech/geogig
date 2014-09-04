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

import org.locationtech.geogig.api.AbstractGeoGigOp;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

/**
 * Adds an entry to the OSM log. The osm is used to keep track of trees created after importing from
 * OSM data, o they can be used as starting points for updating. The log is basically a list of
 * those trees that represent a dataset that correspond to given OSM snapshot and can, therefore, be
 * used to synchronize
 */
public class AddOSMLogEntry extends AbstractGeoGigOp<Void> {

    private OSMLogEntry entry;

    public AddOSMLogEntry setEntry(OSMLogEntry entry) {
        this.entry = entry;
        return this;
    }

    @Override
    protected Void _call() {
        URL file = command(ResolveOSMLogfile.class).call();
        try {
            Files.append(entry.toString() + "\n", new File(file.getFile()), Charsets.UTF_8);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return null;
    }

}
