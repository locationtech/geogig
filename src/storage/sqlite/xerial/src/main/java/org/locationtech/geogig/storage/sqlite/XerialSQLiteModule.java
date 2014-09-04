/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Justin Deoliveira (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.sqlite;

import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StagingDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

/**
 * Module for the Xerial SQLite storage backend.
 * <p>
 * More information about the SQLite jdbc driver available at {@link https
 * ://bitbucket.org/xerial/sqlite-jdbc}.
 * </p>
 * 
 * @author Justin Deoliveira, Boundless
 */
public class XerialSQLiteModule extends AbstractModule {

    static Logger LOG = LoggerFactory.getLogger(XerialSQLiteModule.class);

    @Override
    protected void configure() {
        bind(ConfigDatabase.class).to(XerialConfigDatabase.class).in(Scopes.SINGLETON);
        bind(ObjectDatabase.class).to(XerialObjectDatabase.class).in(Scopes.SINGLETON);
        bind(GraphDatabase.class).to(XerialGraphDatabase.class).in(Scopes.SINGLETON);
        bind(StagingDatabase.class).to(XerialStagingDatabase.class).in(Scopes.SINGLETON);
    }

}
