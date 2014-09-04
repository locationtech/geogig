/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal.history;

import java.util.Calendar;

import javax.xml.bind.DatatypeConverter;

import com.vividsolutions.jts.geom.Envelope;

/**
 *
 */
class ParsingUtils {

    /**
     * @param xmlDateTime
     * @return
     */
    public static long parseDateTime(String xmlDateTime) {
        try {
            Calendar cal = DatatypeConverter.parseDateTime(xmlDateTime);
            cal.set(Calendar.MILLISECOND, 0);
            long timestamp = cal.getTimeInMillis();

            return timestamp;
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw new RuntimeException(
                    String.format("Unable to parse timestamp '%s'", xmlDateTime), e);
        }
    }

    public static Envelope parseWGS84Bounds(String minLat, String minLon, String maxLat,
            String maxLon) {
        double minx = Double.valueOf(minLon);
        double miny = Double.valueOf(minLat);
        double maxx = Double.valueOf(maxLon);
        double maxy = Double.valueOf(maxLat);
        return new Envelope(minx, maxx, miny, maxy);
    }

}
