/* Copyright (c) 2015 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.geopkg;

import java.util.concurrent.atomic.AtomicLong;

import org.locationtech.geogig.rocksdb.RocksdbMap;

public class AuditReport {

    public final AuditTable table;

    public final AtomicLong added = new AtomicLong(), removed = new AtomicLong(),
            changed = new AtomicLong();

    public RocksdbMap<String, String> newMappings = null;

    AuditReport(AuditTable table) {
        this.table = table;
    }

    public void addMapping(String key, String value) {
        if (newMappings == null) {
            newMappings = new RocksdbMap<String, String>();
        }
        newMappings.put(key, value);
    }
}