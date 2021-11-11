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

import static org.locationtech.geogig.base.Preconditions.checkArgument;

import java.io.File;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standard platform for GeoGig.
 */
public class DefaultPlatform implements Platform {

    private static final long serialVersionUID = 1L;

    protected File workingDir;

    /**
     * @return the working directory
     */
    public @Override File pwd() {
        if (workingDir != null) {
            return workingDir;
        }
        return new File(".").getAbsoluteFile().getParentFile();
    }

    /**
     * @param workingDir the working directory to use
     * @throws IllegalArgumentException if {@code workingDir} does not exist or is not a directory
     */
    public @Override void setWorkingDir(File workingDir) {
        checkArgument(workingDir == null || workingDir.isDirectory(),
                "file does not exist or is not a directory: " + workingDir);
        this.workingDir = workingDir;
    }

    /**
     * @see Platform#whoami()
     */
    public @Override String whoami() {
        return System.getProperty("user.name", "nobody");
    }

    /**
     * @return the current time in milliseconds
     * @see org.locationtech.geogig.repository.Platform#currentTimeMillis()
     */
    // Make sure that all the times are unique (make sure clock ticks between calls)
    private static final AtomicLong lastTick = new AtomicLong();

    public @Override long currentTimeMillis() {
        final long currentTime = System.currentTimeMillis();
        long unique = lastTick.updateAndGet(lastReturnedTime -> {
            if (lastReturnedTime == currentTime) {
                return currentTime + 1;
            }
            if (lastReturnedTime > currentTime) {
                return lastReturnedTime + 1;
            }
            return currentTime;
        });
        return unique;
    }

    /**
     * @return the user home directory
     */
    public @Override File getUserHome() {
        return new File(System.getProperty("user.home"));
    }

    public @Override int timeZoneOffset(long timeStamp) {
        return TimeZone.getDefault().getOffset(timeStamp);
    }

    public @Override long nanoTime() {
        return System.nanoTime();
    }

    public @Override int availableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

}
