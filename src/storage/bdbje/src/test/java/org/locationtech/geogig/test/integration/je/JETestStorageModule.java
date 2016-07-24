/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration.je;

import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.bdbje.EnvironmentBuilder;
import org.locationtech.geogig.storage.bdbje.JEGraphDatabase_v0_2;
import org.locationtech.geogig.storage.bdbje.JEObjectDatabase_v0_2;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

/**
 *
 */
public class JETestStorageModule extends AbstractModule {

    @Override
    protected void configure() {
        // BDB JE bindings for the different kinds of databases
        bind(ObjectDatabase.class).to(JEObjectDatabase_v0_2.class).in(Scopes.SINGLETON);
        bind(GraphDatabase.class).to(JEGraphDatabase_v0_2.class).in(Scopes.SINGLETON);

        // this module's specific. Used by the JE*Databases to set up the db environment
        // A new instance of each db
        bind(EnvironmentBuilder.class).in(Scopes.NO_SCOPE);
    }

}
