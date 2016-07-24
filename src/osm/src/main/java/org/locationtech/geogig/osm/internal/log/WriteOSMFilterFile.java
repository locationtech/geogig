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

import com.google.common.base.Preconditions;

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

        BlobStore blobStore = context().blobStore();
        final String logPath = "osm/log/";
        final String filterPath = logPath + "filter" + entry.getId();
        Blobs.putBlob(blobStore, filterPath, filter);
        return null;
    }

}
