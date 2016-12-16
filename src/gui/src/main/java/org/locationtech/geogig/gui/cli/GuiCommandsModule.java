/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.gui.cli;

import org.locationtech.geogig.cli.CLIModule;

import com.google.inject.AbstractModule;

/**
 * @see Map
 */
public class GuiCommandsModule extends AbstractModule implements CLIModule {

    @Override
    protected void configure() {
        bind(Map.class);
    }

}
