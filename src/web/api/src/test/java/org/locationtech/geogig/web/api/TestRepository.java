/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.io.File;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.api.porcelain.InitOp;
import org.locationtech.geogig.cli.test.functional.general.CLITestContextBuilder;

/**
 * JUnit {@link Rule} to set up a temporary repository.
 * <p>
 * The temporary repository is lazily built at {@link TestRepository#getGeogig()}
 *
 */
public class TestRepository extends ExternalResource {

    private TemporaryFolder tmpFolder;

    private GeoGIG geogig;

    private File repoDir;

    @Override
    protected void before() throws Throwable {
        tmpFolder = new TemporaryFolder();
        tmpFolder.create();
    }

    @Override
    protected void after() {
        try {
            if (geogig != null) {
                geogig.close();
                geogig = null;
            }
        } finally {
            tmpFolder.delete();
        }
    }

    public GeoGIG createRpository(String name) {
        File dataDirectory = tmpFolder.getRoot();
        repoDir = new File(dataDirectory, name);
        Assert.assertTrue(repoDir.mkdir());

        TestPlatform testPlatform = new TestPlatform(dataDirectory);
        GlobalContextBuilder.builder = new CLITestContextBuilder(testPlatform);
        GeoGIG geogig = new GeoGIG(repoDir);
        geogig.command(InitOp.class).call();
        return geogig;
    }

    public TemporaryFolder tmpFolder() {
        return this.tmpFolder;
    }

    public File repoDirectory() {
        return repoDir;
    }

    public GeoGIG getGeogig() {
        if (this.geogig == null) {
            this.geogig = createRpository("testrepo");
        }
        return geogig;
    }

    private Object lastCommandResult;

    @SuppressWarnings("unchecked")
    public <T> T get() {
        return (T) lastCommandResult;
    }
}
