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

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.Blobs;

import com.google.common.base.Optional;

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
        BlobStore blobStore = context().blobStore();
        final String logPath = "osm/log";

        Optional<String> log = Blobs.getBlobAsString(blobStore, logPath);
        StringBuilder newLog = new StringBuilder();
        if (log.isPresent()) {
            newLog.append(log.get());
        }
        newLog.append(entry.toString()).append('\n');
        Blobs.putBlob(blobStore, logPath, newLog);
        return null;
    }

}
