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

import org.locationtech.geogig.osm.internal.Mapping;
import org.locationtech.geogig.osm.internal.MappingRule;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.Blobs;

import com.google.common.base.Preconditions;

/**
 * Writes the mapping blobs that store the information about a mapping operation, storing the id
 * from which the affected trees have been mapped and the mapping code used
 */
public class WriteOSMMappingEntries extends AbstractGeoGigOp<Void> {

    private Mapping mapping;

    private OSMMappingLogEntry entry;

    public WriteOSMMappingEntries setMappingLogEntry(OSMMappingLogEntry entry) {
        this.entry = entry;
        return this;
    }

    public WriteOSMMappingEntries setMapping(Mapping mapping) {
        this.mapping = mapping;
        return this;
    }

    @Override
    protected Void _call() {
        Preconditions.checkNotNull(entry);
        Preconditions.checkNotNull(mapping);

        BlobStore blobStore = context().blobStore();
        final String pathPrefix = "osm/map/";
        for (MappingRule rule : mapping.getRules()) {
            String path = pathPrefix + rule.getName();
            Blobs.putBlob(blobStore, path, entry.toString());
        }
        String path = pathPrefix + entry.getPostMappingId();
        Blobs.putBlob(blobStore, path, mapping.toString());

        return null;
    }
}
