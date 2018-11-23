/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.storage;

import org.locationtech.geogig.cli.CLIModule;

import com.google.inject.AbstractModule;

public class StorageCommandsModule extends AbstractModule implements CLIModule {

    protected @Override void configure() {
        bind(LsRepos.class);
        bind(PGStorageUpgrade.class);
        bind(PGCreateDDL.class);
    }

}
