/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.geogig.web.functional;

import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.di.HintsModule;
import org.locationtech.geogig.di.PluginsModule;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.impl.ContextBuilder;
import org.locationtech.geogig.repository.impl.PluginsContextBuilder;
import org.locationtech.geogig.test.TestPlatform;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.util.Modules;

public class FunctionalRepoContextBuilder extends ContextBuilder {

    private TestPlatform platform;

    public FunctionalRepoContextBuilder(TestPlatform platform) {
        this.platform = platform;
    }

    @Override
    public Context build(Hints hints) {
        FunctionalTestModule functionalTestModule = new FunctionalTestModule(platform.clone());

        Context context = Guice.createInjector(
                Modules.override(new GeogigModule(), new HintsModule(hints))
                        .with(new PluginsModule(), new PluginsContextBuilder.DefaultPlugins(),
                                functionalTestModule))
                .getInstance(Context.class);
        return context;
    }

    private static class FunctionalTestModule extends AbstractModule {

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
}
