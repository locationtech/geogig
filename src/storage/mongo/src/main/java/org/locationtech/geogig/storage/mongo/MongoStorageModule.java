/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.storage.mongo;

import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StagingDatabase;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class MongoStorageModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ObjectDatabase.class).to(MongoObjectDatabase.class).in(Scopes.SINGLETON);
        bind(StagingDatabase.class).to(MongoStagingDatabase.class).in(Scopes.SINGLETON);
        bind(GraphDatabase.class).to(MongoGraphDatabase.class).in(Scopes.SINGLETON);
        bind(MongoConnectionManager.class).in(Scopes.NO_SCOPE);
    }
}
