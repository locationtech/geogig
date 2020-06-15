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

    public @Override File pwd() {
        return workingDir;
    }

    public @Override File getUserHome() {
        return userHomeDirectory;
    }

    public void setUserHome(File userHomeDirectory) {
        this.userHomeDirectory = userHomeDirectory;
    }

    public @Override TestPlatform clone() {
        return new TestPlatform(pwd(), getUserHome());
    }

    public @Override String toString() {
        return getClass().getSimpleName() + "[home=" + this.userHomeDirectory + ", pwd="
                + super.workingDir + "]";
    }
}
