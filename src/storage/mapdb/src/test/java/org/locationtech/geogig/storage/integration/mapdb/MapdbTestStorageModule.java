/* Copyright (c) 2015 SWM Services GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Sebastian Schmidt (SWM Services GmbH) - initial implementation
 */
package org.locationtech.geogig.storage.integration.mapdb;

import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.mapdb.MapdbGraphDatabase;
import org.locationtech.geogig.storage.mapdb.MapdbObjectDatabase;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class MapdbTestStorageModule extends AbstractModule {
    @Override
    protected void configure() {
        // Mapdb bindings for the different kinds of databases
        bind(ObjectDatabase.class).to(MapdbObjectDatabase.class).in(Scopes.SINGLETON);
        bind(GraphDatabase.class).to(MapdbGraphDatabase.class).in(Scopes.SINGLETON);
    }
}
