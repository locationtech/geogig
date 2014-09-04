/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.performance.je;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.test.integration.je.JETestStorageModule;
import org.locationtech.geogig.test.performance.LogOpPerformanceTest;

import com.google.inject.Guice;
import com.google.inject.util.Modules;

public class JELogOpPerformanceTest extends LogOpPerformanceTest {
    @Override
    protected Context createInjector() {
        return Guice.createInjector(
                Modules.override(new GeogigModule()).with(new JETestStorageModule())).getInstance(
                Context.class);
    }
}
