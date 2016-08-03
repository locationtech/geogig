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
        BlobStore blobStore = context().blobStore();
        final String pathPrefix = "osm/map/";
        final String path = pathPrefix + this.path;
        Optional<String> blob = Blobs.getBlobAsString(blobStore, path);
        OSMMappingLogEntry entry = null;
        if (blob.isPresent()) {
            entry = OSMMappingLogEntry.fromString(blob.get());
        }
        return Optional.fromNullable(entry);
    }
}
