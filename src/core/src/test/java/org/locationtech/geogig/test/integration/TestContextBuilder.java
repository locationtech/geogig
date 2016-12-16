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

import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.di.HintsModule;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.impl.ContextBuilder;
import org.locationtech.geogig.test.MemoryModule;

import com.google.inject.Guice;
import com.google.inject.util.Modules;

public class TestContextBuilder extends ContextBuilder {

    private Platform platform;

    public TestContextBuilder() {
        super();
    }

    public TestContextBuilder(Platform platform) {
        this.platform = platform;
    }

    @Override
    public Context build(Hints hints) {
        if (!hints.get(Hints.PLATFORM).isPresent() && this.platform != null) {
            hints = hints.platform(platform);
        }
        return Guice.createInjector(Modules.override(new GeogigModule()).with(new MemoryModule(),
                new HintsModule(hints))).getInstance(Context.class);
    }

}
