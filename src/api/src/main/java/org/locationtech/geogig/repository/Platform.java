/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.io.File;
import java.io.Serializable;

/**
 * Interface for a GeoGig platform.
 * 
 * @since 1.0
 */
public interface Platform extends Serializable{

    /**
     * @return the working directory
     */
    public File pwd();

    /**
     * Sets the working directory, or {@code null} to default to the JVM working directory
     */
    public void setWorkingDir(File workingDir);

    /**
     * @return who I am
     */
    public String whoami();

    /**
     * @return the current time in milliseconds
     */
    public long currentTimeMillis();

    /**
     * @see System#nanoTime()
     */
    public long nanoTime();

    /**
     * @return the user's home directory
     */
    public File getUserHome();

    /**
     * Returns the offset of the platform's time zone from UTC at the specified timeStamp.
     * 
     * @param timeStamp the date represented in milliseconds since January 1, 1970 00:00:00 GMT
     * @return the amount of time in milliseconds to add to UTC to get local time.
     */
    public int timeZoneOffset(long timeStamp);

    /**
     * @return the maximum number of processors available to the virtual machine; never smaller than
     *         one, as in {@link Runtime#availableProcessors()}
     */
    public int availableProcessors();

}
