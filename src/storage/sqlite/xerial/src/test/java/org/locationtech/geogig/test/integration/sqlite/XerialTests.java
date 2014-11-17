/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.test.integration.sqlite;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.storage.sqlite.Xerial;
import org.locationtech.geogig.storage.sqlite.XerialSQLiteModule;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.util.Modules;

/**
 * Test utility class.
 * 
 * @author Justin Deoliveira, Boundless
 * 
 */
public class XerialTests {

    /**
     * Creates the injector to enable xerial sqlite storage.
     */
    public static Context injector(final TestPlatform platform) {
        return Guice.createInjector(Modules.override(new GeogigModule()).with(
                new XerialSQLiteModule(), new AbstractModule() {
                    @Override
                    protected void configure() {
                        Xerial.turnSynchronizationOff();
                        bind(Platform.class).toInstance(platform);
                    }
                })).getInstance(Context.class);
    }
}
