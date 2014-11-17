/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api;

import com.google.common.base.Optional;

/**
 * The GeoGig identity of a single individual, composed of a name and email address.
 */
public interface RevPerson {

    /**
     * @return the name
     */
    public abstract Optional<String> getName();

    /**
     * @return the email
     */
    public abstract Optional<String> getEmail();

    /**
     * @return this person's timestamp, as milliseconds since January 1, 1970, 00:00:00 GMT
     */
    public abstract long getTimestamp();

    /**
     * @return the time zone offset from UTC, in milliseconds
     */
    public abstract int getTimeZoneOffset();

}