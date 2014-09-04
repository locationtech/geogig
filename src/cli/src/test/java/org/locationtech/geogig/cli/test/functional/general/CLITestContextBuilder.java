/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.test.functional.general;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ContextBuilder;
import org.locationtech.geogig.api.TestPlatform;
import org.locationtech.geogig.cli.CLIContextBuilder;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.di.PluginsModule;
import org.locationtech.geogig.di.caching.CachingModule;
import org.locationtech.geogig.repository.Hints;

import com.google.inject.Guice;
import com.google.inject.util.Modules;

public class CLITestContextBuilder extends ContextBuilder {

    private TestPlatform platform;

    public CLITestContextBuilder(TestPlatform platform) {
        this.platform = platform;
    }

    @Override
    public Context build(Hints hints) {
        FunctionalTestModule functionalTestModule = new FunctionalTestModule(platform.clone());

        Context context = Guice.createInjector(
                Modules.override(new GeogigModule()).with(new PluginsModule(),
                        new CLIContextBuilder.DefaultPlugins(), functionalTestModule,
                        new HintsModule(hints), new CachingModule())).getInstance(Context.class);
        return context;
    }

}
