/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api;

import java.io.File;

public class TestPlatform extends DefaultPlatform implements Platform, Cloneable {

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
}
