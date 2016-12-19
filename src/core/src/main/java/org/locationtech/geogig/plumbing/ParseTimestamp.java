/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

import javax.xml.datatype.DatatypeConfigurationException;

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Platform;

import com.google.common.base.Preconditions;

/**
 * Parses a string representing a timestamp.
 * 
 * Supported formats are:
 * 
 * <ul>
 * <li>Standard format supported by the DateFormat class in the current locale
 * <li>git-like time strings (yesterday, 2.days.ago, etc)
 * <li>a {@code long} representing miliseconds since the standard UNIX epoch
 * </ul>
 * 
 */
public class ParseTimestamp extends AbstractGeoGigOp<Long> {

    private String string;

    private static HashMap<String, Integer> units = new HashMap<String, Integer>();

    static {
        units.put("second", 1000);
        units.put("minute", 1000 * 60);
        units.put("hour", 1000 * 60 * 60);
        units.put("day", 1000 * 60 * 60 * 24);
        units.put("week", 1000 * 60 * 60 * 24 * 7);
        units.put("year", 1000 * 60 * 60 * 24 * 365);
    }

    /**
     * @param string the String to parse
     * @return {@code this}
     */
    public ParseTimestamp setString(String string) {
        this.string = string;
        return this;
    }

    /**
     * Parses a string with a timestamp
     * 
     * @return a Long with the timestamp represented by the specified string
     */
    @Override
    protected Long _call() {
        Preconditions.checkState(string != null, "String has not been set.");

        try { // see if it is a timestamp in milisecs
            Long milis = new Long(string);
            return milis;
        } catch (NumberFormatException e) {
        }

        SimpleDateFormat formatter;
        final Platform platform = platform();
        if (string.equals("yesterday")) { // return the beginning of yesterday, not just 24h ago
                                          // from current time
            try {
                formatter = new SimpleDateFormat("dd/MM/yyyy");
                Date today = new Date(platform.currentTimeMillis());
                long todayOnlyDate = formatter.parse(formatter.format(today)).getTime();
                long millisecsInOneDay = 60 * 60 * 24 * 1000;
                long yesterday = todayOnlyDate - millisecsInOneDay;
                return yesterday;
            } catch (ParseException e) {
                // shouldn't reach this
            }
        }

        if (string.equals("today")) {
            try {
                formatter = new SimpleDateFormat("dd/MM/yyyy");
                Date today = new Date(platform.currentTimeMillis());
                long todayOnlyDate = formatter.parse(formatter.format(today)).getTime();
                return todayOnlyDate;
            } catch (ParseException e) {
                // shouldn't reach this
            }
        }

        // parse it as a git-like time reference
        String[] tokens = string.split("\\.");
        if (tokens.length % 2 != 0) {
            if (tokens[tokens.length - 1].toLowerCase().equals("ago")) {
                long currentTime = platform.currentTimeMillis();
                int i;
                for (i = 0; i < tokens.length - 1; i++) {
                    try {
                        double number = Double.parseDouble(tokens[i]);
                        i++;
                        String s = tokens[i].toLowerCase();
                        if (s.endsWith("s")) {
                            s = s.substring(0, s.length() - 1);
                        }
                        if (units.containsKey(s)) {
                            currentTime -= units.get(s) * number;
                        } else {
                            break;
                        }
                    } catch (Exception e) {
                        break;
                    }
                }
                if (i == tokens.length - 1) {
                    return currentTime;
                }
            }
        }

        // finally, try to parse it as a Date object
        try {
            long time = javax.xml.datatype.DatatypeFactory.newInstance()
                    .newXMLGregorianCalendar(string).toGregorianCalendar().getTimeInMillis();
            return time;
        } catch (DatatypeConfigurationException e) {
        } catch (IllegalArgumentException e) {
        }

        throw new IllegalArgumentException("Invalid timestamp string: " + string);

    }
}
