/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test;

import java.io.File;

import org.locationtech.geogig.repository.DefaultPlatform;
import org.locationtech.geogig.repository.Platform;

public class TestPlatform extends DefaultPlatform implements Platform, Cloneable {

    private static final long serialVersionUID = 1L;

    private File userHomeDirectory;

    public TestPlatform(final File workingDirectory) {
        super.workingDir = workingDirectory;
        this.userHomeDirectory = new File(workingDirectory, "userhome");
        this.userHomeDirectory.mkdir();
    }

    public TestPlatform(final File workingDirectory, final File userHomeDirectory) {
        super.workingDir = workingDirectory;
        this.userHomeDirectory = userHomeDirectory;
    }

    @Override
    public File pwd() {
        return workingDir;
    }

    @Override
    public File getUserHome() {
        return userHomeDirectory;
    }

    public void setUserHome(File userHomeDirectory) {
        this.userHomeDirectory = userHomeDirectory;
    }

    @Override
    public TestPlatform clone() {
        return new TestPlatform(pwd(), getUserHome());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[home=" + this.userHomeDirectory + ", pwd="
                + super.workingDir + "]";
    }

    //Make sure that all the times are unique (make sure clock ticks between calls)
    @Override
    public synchronized long currentTimeMillis() {
        boolean keep_going = true;
        int i = 0;
        long current = super.currentTimeMillis();
        while (keep_going) {
            if (current <= lastCreatedTimestamp) {
                try {
                    Thread.sleep(1);
                } catch (Exception e) {
                    //do nothing
                }
            } else {
                lastCreatedTimestamp = current;
                return current;
            }
            i++;
            keep_going = i < 50; // don't run forever -- this should never be a problem (except for system clock resets)
            current = super.currentTimeMillis();
        }
        return current; //waited too long
    }

    static volatile long lastCreatedTimestamp = 0;
}
