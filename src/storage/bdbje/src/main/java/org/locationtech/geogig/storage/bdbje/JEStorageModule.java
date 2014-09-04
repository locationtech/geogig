/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.bdbje;

import org.locationtech.geogig.storage.DeduplicationService;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StagingDatabase;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

/**
 *
 */
public class JEStorageModule extends AbstractModule {

    @Override
    protected void configure() {
        // BDB JE bindings for the different kinds of databases
        bind(ObjectDatabase.class).to(JEObjectDatabase_v0_1.class).in(Scopes.SINGLETON);
        bind(StagingDatabase.class).to(JEStagingDatabase_v0_1.class).in(Scopes.SINGLETON);
        bind(DeduplicationService.class).to(BDBJEDeduplicationService.class).in(Scopes.SINGLETON);
        bind(GraphDatabase.class).to(JEGraphDatabase_v0_1.class).in(Scopes.SINGLETON);

        // this module's specific. Used by the JE*Databases to set up the db environment
        // A new instance of each db
        bind(EnvironmentBuilder.class).in(Scopes.NO_SCOPE);
    }

}
