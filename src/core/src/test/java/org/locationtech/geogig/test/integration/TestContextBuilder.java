/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import java.util.Arrays;
import java.util.List;

import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.di.HintsModule;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.impl.ContextBuilder;
import org.locationtech.geogig.test.MemoryModule;

import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class TestContextBuilder extends ContextBuilder {

    private Platform platform;

    private Module[] additionalOverrides;

    public TestContextBuilder() {
        super();
    }

    public TestContextBuilder(Platform platform) {
        this.platform = platform;
    }

    public TestContextBuilder(Platform platform, Module... overrides) {
        this.platform = platform;
        this.additionalOverrides = overrides;
    }

    @Override
    public Context build(Hints hints) {
        if (!hints.get(Hints.PLATFORM).isPresent() && this.platform != null) {
            hints = hints.platform(platform);
        }

        Module[] overrides = { new MemoryModule(), new HintsModule(hints) };

        if (this.additionalOverrides != null) {
            List<Module> list = Lists.newArrayList(overrides);
            list.addAll(Arrays.asList(additionalOverrides));
            overrides = list.toArray(new Module[list.size()]);
        }

        GeogigModule geogigModule = new GeogigModule();
        Module override = Modules.override(geogigModule).with(overrides);

        return Guice.createInjector(override).getInstance(Context.class);
    }

}
