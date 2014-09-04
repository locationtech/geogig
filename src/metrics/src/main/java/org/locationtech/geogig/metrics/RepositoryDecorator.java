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
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.Repository.RepositoryListener;

/**
 * Shuts down the metrics service at repository close() event
 */
class RepositoryDecorator implements Decorator {

    private HeapMemoryMetricsService service;

    private RepositoryListener listener;

    public RepositoryDecorator(HeapMemoryMetricsService service) {
        this.service = service;
    }

    @Override
    public boolean canDecorate(Object instance) {
        return instance instanceof Repository;
    }

    @Override
    public <I> I decorate(I subject) {
        if (listener == null) {
            listener = new RepositoryListener() {

                @Override
                public void opened(Repository repo) {
                    service.startAsync().awaitRunning();
                }

                @Override
                public void closed() {
                    service.stopAsync();
                }
            };
            ((Repository) subject).addListener(listener);
        }
        return subject;
    }
}
