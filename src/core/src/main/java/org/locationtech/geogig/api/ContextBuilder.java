/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api;

import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.repository.Hints;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;

public class ContextBuilder {

    public final Context build() {
        return build(new Hints());
    }

    /**
     * @param hints a set of hints to pass over to the injector to be injected into components that
     *        can make use of it
     */
    public Context build(Hints hints) {
        return Guice.createInjector(new GeogigModule(), new HintsModule(hints)).getInstance(
                org.locationtech.geogig.api.Context.class);
    }

    protected static class HintsModule extends AbstractModule {
        private Hints instance;

        public HintsModule(Hints instance) {
            this.instance = instance;

        }

        @Override
        protected void configure() {
            bind(Hints.class).toInstance(instance);
        }
    }

}
