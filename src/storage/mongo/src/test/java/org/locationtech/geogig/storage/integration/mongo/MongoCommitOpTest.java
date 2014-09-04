/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.integration.mongo;

import java.io.File;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.di.GeogigModule;

import com.google.common.base.Throwables;
import com.google.inject.Guice;
import com.google.inject.util.Modules;

public class MongoCommitOpTest extends org.locationtech.geogig.test.integration.CommitOpTest {
    @Rule
    public TemporaryFolder mockWorkingDirTempFolder = new TemporaryFolder();

    @Override
    protected Context createInjector() {
        File workingDirectory;
        try {
            workingDirectory = mockWorkingDirTempFolder.getRoot();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        Platform testPlatform = new TestPlatform(workingDirectory);
        return Guice.createInjector(
                Modules.override(new GeogigModule()).with(new MongoTestStorageModule(),
                        new TestModule(testPlatform))).getInstance(Context.class);
    }

}
