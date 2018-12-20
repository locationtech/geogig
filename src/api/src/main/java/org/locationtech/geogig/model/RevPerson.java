/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import com.google.common.base.Optional;

/**
 * The GeoGig identity of a single individual, composed of a name and email address, as used to
 * track authorship in {@link RevCommit commits} and {@link RevTag tags}.
 * 
 * @see RevCommit
 * @see RevTag
 * 
 * @since 1.0
 */
public interface RevPerson {

    /**
     * @return the person's name, if present
     */
    public abstract Optional<String> getName();

    /**
     * @return the person's email address, if present
     */
    public abstract Optional<String> getEmail();

    /**
     * @return this timestamp at which this person created the revision object it's been accountable
     *         for, as milliseconds since January 1, 1970, 00:00:00 GMT
     */
    public abstract long getTimestamp();

    /**
     * @return the time zone offset from UTC, in milliseconds
     */
    public abstract int getTimeZoneOffset();

    public static RevPersonBuilder builder() {
        return new RevPersonBuilder();
    }
}