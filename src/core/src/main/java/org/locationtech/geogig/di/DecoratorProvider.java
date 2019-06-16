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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.locationtech.geogig.hooks.CommandHooksDecorator;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.collect.Maps;

import lombok.NonNull;

class DecoratorProvider {

    private Set<Decorator> decorators;

    private Map<Class<?>, Object> singletonDecorators = Maps.newConcurrentMap();

    public DecoratorProvider() {
        this.decorators = new HashSet<>();
        decorators.add(new CommandHooksDecorator());
        decorators.add(new ConflictInterceptor());
    }

    @SuppressWarnings("unchecked")
    public <T> T get(@NonNull final T undecorated) {
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
