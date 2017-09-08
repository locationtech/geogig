/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Justin Deoliveira (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.cli.bindings;

import org.locationtech.geogig.cli.CLIModule;
import org.locationtech.geogig.spring.cli.commands.Serve;

import com.google.inject.Binder;

/**
 * Hooks up into the CLI commands through the {@link CLIModule} SPI lookup by means of the
 * {@code META-INF/services/org.locationtech.geogig.cli.CLIModule} text file, and binds the
 * {@link Serve} command.
 *
 */
public class ServeModule implements CLIModule {

    @Override
    public void configure(Binder binder) {
        binder.bind(Serve.class);
    }

}
