/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.di.caching;

import org.locationtech.geogig.storage.ConfigDatabase;

import com.google.inject.Inject;
import com.google.inject.Provider;

class StagingDatabaseCacheFactory extends CacheFactory {

    @Inject
    public StagingDatabaseCacheFactory(Provider<ConfigDatabase> configDb) {
        super("stagingdb.cache", configDb);
    }

}
