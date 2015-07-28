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

import jline.internal.Preconditions;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.osm.internal.Mapping;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.Blobs;

import com.google.common.base.Optional;

/**
 * Reads the mapping associated to a previously executed OSM mapping operation.
 */
public class ReadOSMMapping extends AbstractGeoGigOp<Optional<Mapping>> {

    private OSMMappingLogEntry entry;

    public ReadOSMMapping setEntry(OSMMappingLogEntry entry) {
        this.entry = entry;
        return this;
    }

    @Override
    protected Optional<Mapping> _call() {
        Preconditions.checkNotNull(entry);
        BlobStore blobStore = context().blobStore();
        final String pathPrefix = "osm/map/";
        final String path = pathPrefix + entry.getPostMappingId();
        Optional<String> blob = Blobs.getBlobAsString(blobStore, path);
        Mapping mapping = null;
        if (blob.isPresent()) {
            mapping = Mapping.fromString(blob.get());
        }
        return Optional.fromNullable(mapping);
    }
}
