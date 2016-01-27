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

import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.Blobs;

/**
 * Returns the set of entries of the OSM log in the current repository.
 */
public class ReadOSMLogEntries extends AbstractGeoGigOp<List<OSMLogEntry>> {

    @Override
    protected List<OSMLogEntry> _call() {
        BlobStore blobStore = context.blobStore();

        List<String> encodedEntries = Blobs.readLines(blobStore, "osm/log");
        List<OSMLogEntry> entries = new ArrayList<>(encodedEntries.size());
        for (String encodedEntry : encodedEntries) {
            OSMLogEntry entry = OSMLogEntry.valueOf(encodedEntry);
            entries.add(entry);
        }
        return entries;
    }

}
