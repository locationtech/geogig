/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal.history;

import static org.locationtech.geogig.osm.internal.history.ParsingUtils.parseDateTime;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class ParsingUtilsTest extends Assert {

    @Test
    public void test() {
        long dateTime = parseDateTime("2009-10-11T20:02:09Z");
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cal.setTimeInMillis(dateTime);
        assertEquals(2009, cal.get(Calendar.YEAR));
        assertEquals(9, cal.get(Calendar.MONTH));
        assertEquals(11, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(20, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(2, cal.get(Calendar.MINUTE));
        assertEquals(9, cal.get(Calendar.SECOND));
        assertEquals(0, cal.get(Calendar.MILLISECOND));
    }

}
