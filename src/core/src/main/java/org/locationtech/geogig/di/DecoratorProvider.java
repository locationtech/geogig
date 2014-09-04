/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.di;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;
import com.google.inject.Inject;

public class DecoratorProvider {

    private Set<Decorator> decorators;

    private Map<Class<?>, Object> singletonDecorators = Maps.newConcurrentMap();

    @Inject
    public DecoratorProvider(Set<Decorator> decorators) {
        this.decorators = decorators;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(final T undecorated) {
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
        if (c.isAnnotationPresent(Singleton.class)) {
            return true;
        }
        Class<?> s = c.getSuperclass();
        if (s != null && isSingleton(s)) {
            return true;
        }
        Class<?>[] interfaces = c.getInterfaces();
        for (Class<?> i : interfaces) {
            return isSingleton(i);
        }
        return false;
    }

}
