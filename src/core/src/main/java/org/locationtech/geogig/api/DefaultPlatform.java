/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.File;
import java.net.URI;
import java.util.TimeZone;

import org.locationtech.geogig.api.plumbing.ResolveGeogigURI;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Standard platform for GeoGig.
 */
public class DefaultPlatform implements Platform {

    protected File workingDir;

    /**
     * @return the working directory
     */
    @Override
    public File pwd() {
        if (workingDir != null) {
            return workingDir;
        }
        return new File(".").getAbsoluteFile().getParentFile();
    }

    /**
     * @param workingDir the working directory to use
     * @throws IllegalArgumentException if {@code workingDir} does not exist or is not a directory
     */
    @Override
    public void setWorkingDir(File workingDir) {
        checkArgument(workingDir == null || workingDir.isDirectory(),
                "file does not exist or is not a directory: " + workingDir);
        this.workingDir = workingDir;
    }

    /**
     * @see Platform#whoami()
     */
    @Override
    public String whoami() {
        return System.getProperty("user.name", "nobody");
    }

    /**
     * @return the current time in milliseconds
     * @see org.locationtech.geogig.api.Platform#currentTimeMillis()
     */
    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * @return the user home directory
     */
    @Override
    public File getUserHome() {
        return new File(System.getProperty("user.home"));
    }

    @Override
    public int timeZoneOffset(long timeStamp) {
        return TimeZone.getDefault().getOffset(timeStamp);
    }

    @Override
    public long nanoTime() {
        return System.nanoTime();
    }

    @Override
    public synchronized File getTempDir() {
        Optional<URI> url = new ResolveGeogigURI(this, null).call();
        final File tmpDir;
        if (url.isPresent()) {
            URI uri = url.get();
            tmpDir = new File(new File(uri), "tmp");
            Preconditions.checkState(tmpDir.exists() || tmpDir.mkdir(),
                    "unable to create directory %s", tmpDir.getAbsolutePath());
        } else {
            String systmplocation = System.getProperty("java.io.tmpdir");
            tmpDir = new File(systmplocation);
        }
        return tmpDir;
    }

    @Override
    public int availableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

}
