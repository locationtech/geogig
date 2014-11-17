/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.di;

import java.lang.reflect.Method;
import java.util.Arrays;

import com.google.common.base.Throwables;
import com.google.inject.matcher.AbstractMatcher;

public class MethodMatcher extends AbstractMatcher<Method> {

    private final Method target;

    public MethodMatcher(Method target) {
        this.target = target;
    }

    public MethodMatcher(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            this.target = clazz.getMethod(methodName, parameterTypes);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public boolean matches(final Method t) {

        return !t.isSynthetic() && target.getName().equals(t.getName())
                && Arrays.equals(target.getParameterTypes(), t.getParameterTypes());
    }

}
