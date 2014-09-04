/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ContextBuilder;
import org.locationtech.geogig.api.MemoryModule;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.repository.Hints;

import com.google.inject.Guice;
import com.google.inject.util.Modules;

public class TestContextBuilder extends ContextBuilder {

    Platform platform;

    public TestContextBuilder(Platform platform) {
        this.platform = platform;
    }

    @Override
    public Context build(Hints hints) {
        return Guice.createInjector(
                Modules.override(new GeogigModule()).with(new MemoryModule(platform),
                        new HintsModule(hints))).getInstance(Context.class);
    }

}
