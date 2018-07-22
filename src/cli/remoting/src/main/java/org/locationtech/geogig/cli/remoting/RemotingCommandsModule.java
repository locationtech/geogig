/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Michael Fawcett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli.remoting;

import org.locationtech.geogig.cli.CLIModule;
import org.locationtech.geogig.cli.GeogigCLI;

import com.google.inject.AbstractModule;

/**
 * Guice module providing remoting commands for the {@link GeogigCLI CLI} app.
 */
public class RemotingCommandsModule extends AbstractModule implements CLIModule {

    @Override
    protected void configure() {
        bind(RemoteExtension.class);
        bind(Clone.class);
        bind(Fetch.class);
        bind(Push.class);
        bind(Pull.class);
    }

}
