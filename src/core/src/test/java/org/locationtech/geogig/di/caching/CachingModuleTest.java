/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.di.caching;

import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Iterator;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.geotools.data.DataUtilities;
import org.geotools.feature.SchemaException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.di.Decorator;
import org.locationtech.geogig.di.DecoratorProvider;
import org.locationtech.geogig.di.GuiceInjector;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.impl.CommitBuilder;
import org.locationtech.geogig.model.impl.RevFeatureTypeBuilder;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;
import org.locationtech.geogig.storage.fs.IniFileConfigDatabase;
import org.locationtech.geogig.storage.impl.ObjectSerializingFactory;
import org.locationtech.geogig.storage.memory.HeapObjectDatabase;
import org.locationtech.geogig.test.TestPlatform;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Modules;

public class CachingModuleTest {

    private ObjectDatabase odb;

    private Cache<ObjectId, RevObject> odbCache;

    private static final RevObject o1 = obj("o1"), o2 = obj("o2"), o3 = ft("o3");

    private static final RevObject s1 = obj("s1"), s2 = obj("s2"), s3 = ft("s3");

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {
        odbCache = mock(Cache.class);

        final ObjectDatabaseCacheFactory odbCacheFac = mock(ObjectDatabaseCacheFactory.class);
        when(odbCacheFac.get()).thenReturn(odbCache);

        File workingDirectory = tmpFolder.getRoot();
        final Platform platform = new TestPlatform(workingDirectory);

        Module module = new AbstractModule() {

            @Override
            protected void configure() {
                bind(Context.class).to(GuiceInjector.class).in(Scopes.SINGLETON);

                Multibinder.newSetBinder(binder(), Decorator.class);
                bind(DecoratorProvider.class).in(Scopes.SINGLETON);

                DataStreamSerializationFactoryV1 sfac = DataStreamSerializationFactoryV1.INSTANCE;
                bind(ObjectSerializingFactory.class).toInstance(sfac);

                bind(ObjectDatabase.class).to(HeapObjectDatabase.class).in(Scopes.SINGLETON);

                ConfigDatabase config = new IniFileConfigDatabase(platform);
                bind(ConfigDatabase.class).toInstance(config);

                bind(ObjectDatabaseCacheFactory.class).toInstance(odbCacheFac);
            }
        };

        Context injector = Guice.createInjector(Modules.override(new CachingModule()).with(module))
                .getInstance(org.locationtech.geogig.repository.Context.class);

        odb = injector.objectDatabase();
        odb.open();

        odb.put(o1);
        odb.put(o2);
        odb.put(o3);

        odb.put(s1);
        odb.put(s2);
        odb.put(s3);
    }

    private static RevObject obj(String name) {
        RevCommit c = new CommitBuilder().setTreeId(ObjectId.NULL).setMessage(name).build();
        return c;
    }

    private static RevFeatureType ft(String name) {
        SimpleFeatureType type;
        try {
            type = DataUtilities.createType("", name, RepositoryTestCase.pointsTypeSpec);
        } catch (SchemaException e) {
            throw Throwables.propagate(e);
        }
        RevFeatureType rft = RevFeatureTypeBuilder.build(type);
        return rft;
    }

    @Test
    public void delete() {
        odb.delete(o1.getId());
        verify(odbCache).invalidate(eq(o1.getId()));

        odb.delete(s1.getId());
        verify(odbCache).invalidate(eq(s1.getId()));
    }

    @Test
    public void deleteAll() {
        Iterator<ObjectId> ids;
        ids = ImmutableList.of(o1.getId(), o2.getId()).iterator();
        odb.deleteAll(ids);
        verify(odbCache, times(1)).invalidate(eq(o1.getId()));
        verify(odbCache, times(1)).invalidate(eq(o2.getId()));

        ids = ImmutableList.of(s1.getId(), s2.getId()).iterator();
        odb.deleteAll(ids);
        verify(odbCache, times(1)).invalidate(eq(s1.getId()));
        verify(odbCache, times(1)).invalidate(eq(s2.getId()));
    }

    @Test
    public void testGetCacheHit() throws ExecutionException {
        when(odbCache.get(eq(o1.getId()), any(Callable.class))).thenReturn(o1);
        assertSame(o1, odb.get(o1.getId()));
    }

}
