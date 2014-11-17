/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.cli.test.functional.general;

import org.locationtech.geogig.api.Platform;

import com.google.inject.AbstractModule;

/**
 * Guice module with tweaks to run functional tests on the target {@link Platform}'s working
 * directory.
 * 
 * @see CLITestContextBuilder
 */
public class FunctionalTestModule extends AbstractModule {

    private Platform testPlatform;

    /**
     * @param testPlatform
     */
    public FunctionalTestModule(Platform testPlatform) {
        this.testPlatform = testPlatform;
    }

    @Override
    protected void configure() {
        if (testPlatform != null) {
            bind(Platform.class).toInstance(testPlatform);
        }
    }

}
