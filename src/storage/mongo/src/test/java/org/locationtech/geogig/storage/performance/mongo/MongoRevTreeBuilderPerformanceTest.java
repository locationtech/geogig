/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.performance.mongo;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.storage.integration.mongo.MongoTestStorageModule;
import org.locationtech.geogig.test.performance.RevTreeBuilderPerformanceTest;

import com.google.inject.Guice;
import com.google.inject.util.Modules;

public class MongoRevTreeBuilderPerformanceTest extends RevTreeBuilderPerformanceTest {
    @Override
    protected Context createInjector() {
        return Guice.createInjector(
                Modules.override(new GeogigModule()).with(new MongoTestStorageModule()))
                .getInstance(Context.class);
    }
}
