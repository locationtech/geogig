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

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.Blobs;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

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
        BlobStore blobStore = context().blobStore();
        final String logPath = "osm/log/";

        final String filterPath = logPath + "filter" + entry.getId();
        Optional<String> filter = Blobs.getBlobAsString(blobStore, filterPath);
        return filter;
    }

}
