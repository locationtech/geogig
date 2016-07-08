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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class AuditReport {

    public final AuditTable table;

    public final AtomicLong added = new AtomicLong(), removed = new AtomicLong(),
            changed = new AtomicLong();

    public final Map<String, String> newMappings = new HashMap<String, String>();

    AuditReport(AuditTable table) {
        this.table = table;
    }
}