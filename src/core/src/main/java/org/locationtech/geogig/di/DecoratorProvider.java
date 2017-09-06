/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.di;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Set;

import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.collect.Maps;
import com.google.inject.Inject;

class DecoratorProvider {

    private Set<Decorator> decorators;

    private Map<Class<?>, Object> singletonDecorators = Maps.newConcurrentMap();

    @Inject
    public DecoratorProvider(Set<Decorator> decorators) {
        this.decorators = decorators;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(final T undecorated) {
        checkNotNull(undecorated);
        T decorated = undecorated;
        Class<? extends Object> undecoratedClass = decorated.getClass();
        {
            Object singletonDecorator = singletonDecorators.get(undecoratedClass);
            if (singletonDecorator != null) {
                return (T) singletonDecorator;
            }
        }

        for (Decorator decorator : decorators) {
            if (decorator.canDecorate(decorated)) {
                decorated = (T) decorator.decorate(decorated);
            }
        }
        if (isSingleton(undecoratedClass)) {
            singletonDecorators.put(undecoratedClass, decorated);
        }

        return decorated;
    }

    private boolean isSingleton(Class<? extends Object> c) {
        return !AbstractGeoGigOp.class.isAssignableFrom(c);
    }

}
