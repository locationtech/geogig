/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.osm.internal.log;

import org.locationtech.geogig.api.ObjectId;

import com.google.common.base.Preconditions;

/**
 * An entry in the OSM log, mapping a given commit representing an snapshot of OSM data created by
 * importing or updating, to the corresponding OSM changeset and its timestamp
 */
public class OSMLogEntry {

    private ObjectId id;

    private long timestamp;

    private long changeset;

    public OSMLogEntry(ObjectId id, long changeset, long timestamp) {
        this.id = id;
        this.changeset = changeset;
        this.timestamp = timestamp;
    }

    public ObjectId getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getChangeset() {
        return changeset;
    }

    public String toString() {
        return id.toString() + "\t" + Long.toString(changeset) + "\t" + Long.toString(timestamp);
    }

    public static OSMLogEntry valueOf(String s) {
        String[] tokens = s.split("\t");
        Preconditions.checkArgument(tokens.length == 3, "wrong OSM log entry definition: %s", s);
        ObjectId id = ObjectId.valueOf(tokens[0]);
        try {
            long changeset = Long.parseLong(tokens[1]);
            long timestamp = Long.parseLong(tokens[2]);
            return new OSMLogEntry(id, changeset, timestamp);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("wrong OSM log entry definition: " + s);
        }
    }
}
