/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.di.caching;

import org.locationtech.geogig.di.Decorator;
import org.locationtech.geogig.di.GeogigModule;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StagingDatabase;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

/**
 * 
 * <p>
 * Depends on {@link GeogigModule} or similar that provides bindings for {@link ConfigDatabase},
 * {@link ObjectDatabase}, and {@link StagingDatabase}.
 * 
 * @see CacheFactory
 * @see ObjectDatabaseCacheInterceptor
 * @see ObjectDatabaseDeleteCacheInterceptor
 * @see ObjectDatabaseDeleteAllCacheInterceptor
 */

public class CachingModule extends AbstractModule {

    /**
     */
    @Override
    protected void configure() {
        // bind separate caches for the object and staging databases

        bind(ObjectDatabaseCacheFactory.class).in(Scopes.SINGLETON);
        bind(StagingDatabaseCacheFactory.class).in(Scopes.SINGLETON);

        Decorator objectCachingDecorator = ObjectDatabaseCacheInterceptor
                .objects(getProvider(ObjectDatabaseCacheFactory.class));

        Decorator indexCachingDecorator = ObjectDatabaseCacheInterceptor
                .staging(getProvider(StagingDatabaseCacheFactory.class));

        GeogigModule.bindDecorator(binder(), objectCachingDecorator);
        GeogigModule.bindDecorator(binder(), indexCachingDecorator);
    }
}
