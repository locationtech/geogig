/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.metrics;

import org.locationtech.geogig.di.Decorator;
import org.locationtech.geogig.storage.ForwardingObjectDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StagingDatabase;

import com.google.inject.Provider;
import com.google.inject.util.Providers;

class ObjectDatabaseDecorator implements Decorator {

    @Override
    public boolean canDecorate(Object instance) {
        return instance instanceof ObjectDatabase && !(instance instanceof StagingDatabase);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I> I decorate(I subject) {
        Provider<ObjectDatabase> provider = Providers.of((ObjectDatabase) subject);
        return (I) new MetricsODB(provider);
    }

    private static class MetricsODB extends ForwardingObjectDatabase {

        public MetricsODB(Provider<? extends ObjectDatabase> odb) {
            super(odb);
        }

    }
}
