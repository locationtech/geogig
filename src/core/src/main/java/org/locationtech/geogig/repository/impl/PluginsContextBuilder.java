/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.di.HintsModule;
import org.locationtech.geogig.di.PluginsModule;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;

import com.google.inject.Guice;
import com.google.inject.util.Modules;

public class PluginsContextBuilder extends ContextBuilderImpl {

    public @Override Context build(Hints hints) {
        return Guice
                .createInjector(Modules.override(new GeogigModule(), new HintsModule(hints))
                        .with(new PluginsModule()))
                .getInstance(org.locationtech.geogig.repository.Context.class);
    }

}
