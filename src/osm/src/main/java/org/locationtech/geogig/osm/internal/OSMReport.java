/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal;

/**
 * A class to store the values that define the result of an OSM operation that alters the current
 * OSM data in the repository, such as applying a diff file or importing new OSM data from an OSM
 * file
 * 
 */
public class OSMReport {

    private long unpprocessedCount;

    private long count;

    private long latestChangeset;

    private long latestTimestamp;

    private long nodeCount;

    private long wayCount;

    public OSMReport(long count, long nodeCount, long wayCount, long unprocessedCount,
            long latestChangeset, long latestTimestamp) {
        this.count = count;
        this.nodeCount = nodeCount;
        this.wayCount = wayCount;
        this.unpprocessedCount = unprocessedCount;
        this.latestChangeset = latestChangeset;
        this.latestTimestamp = latestTimestamp;
    }

    /**
     * The number of features downloaded that could not be processeed and imported
     * 
     * @return
     */
    public long getUnpprocessedCount() {
        return unpprocessedCount;
    }

    /**
     * The total number of downloaded features
     * 
     * @return
     */
    public long getCount() {
        return count;
    }

    public long getNodeCount() {
        return nodeCount;
    }

    public long getWayCount() {
        return wayCount;
    }

    /**
     * The id of the most recent changeset downloaded
     * 
     * @return
     */
    public long getLatestChangeset() {
        return latestChangeset;
    }

    /**
     * the timestamp of the most recent changeset downloaded
     * 
     * @return
     */
    public long getLatestTimestamp() {
        return latestTimestamp;
    }

}
