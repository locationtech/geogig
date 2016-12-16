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
import org.locationtech.geogig.cli.test.functional.CLITestContextBuilder;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.locationtech.geogig.test.TestPlatform;

import com.google.common.base.Preconditions;

/**
 * JUnit {@link Rule} to set up a temporary repository.
 * <p>
 * The temporary repository is lazily built at {@link TestRepository#getGeogig()}
 *
 */
public class TestRepository extends ExternalResource {

    public static final String REPO_NAME = "testrepo";

    private TemporaryFolder tmpFolder;

    private GeoGIG geogig;

    private File repoDir;

    private File homeDir;

    @Override
    protected void before() throws Throwable {
        tmpFolder = new TemporaryFolder();
        tmpFolder.create();
        homeDir = tmpFolder.newFolder("home");
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

    public GeoGIG createGeoGIG(String name) {
        File dataDirectory = tmpFolder.getRoot();
        repoDir = new File(dataDirectory, name);
        Assert.assertTrue(repoDir.mkdir());

        TestPlatform testPlatform = new TestPlatform(repoDir, homeDir);
        GlobalContextBuilder.builder(new CLITestContextBuilder(testPlatform));
        Context context = GlobalContextBuilder.builder().build(new Hints().platform(testPlatform));
        GeoGIG geogig = new GeoGIG(context);
        return geogig;
    }

    public void initializeRpository() {
        Preconditions.checkState(geogig != null);
        geogig.command(InitOp.class).call();
        geogig.getOrCreateRepository();
    }

    public TemporaryFolder tmpFolder() {
        return this.tmpFolder;
    }

    public File repoDirectory() {
        return repoDir;
    }

    public GeoGIG getGeogig() {
        return getGeogig(true);
    }

    public GeoGIG getGeogig(boolean initialized) {
        if (this.geogig == null) {
            this.geogig = createGeoGIG(REPO_NAME);
            if (initialized && null == geogig.getRepository()) {
                initializeRpository();
            }
        }
        return geogig;
    }

    private Object lastCommandResult;

    @SuppressWarnings("unchecked")
    public <T> T get() {
        return (T) lastCommandResult;
    }
}
