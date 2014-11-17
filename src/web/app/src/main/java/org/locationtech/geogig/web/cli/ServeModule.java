/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.cli;

import org.locationtech.geogig.cli.CLIModule;
import org.locationtech.geogig.web.cli.commands.Serve;

import com.google.inject.Binder;

public class ServeModule implements CLIModule {

    @Override
    public void configure(Binder binder) {
        binder.bind(Serve.class);
    }

}
