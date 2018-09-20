/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.geogig.commands.pr;

import org.locationtech.geogig.repository.Context;

import lombok.NonNull;

public class ChaninedCommand<T> extends PRCommand<T> {

    private PRCommand<?> first;

    private PRCommand<T> second;

    public static <T> ChaninedCommand<T> chain(PRCommand<?> first, PRCommand<T> second) {

        return new ChaninedCommand<T>(first, second);
    }

    public @Override ChaninedCommand<T> setContext(Context context) {
        second.setContext(context);
        return this;
    }

    private ChaninedCommand(@NonNull PRCommand<?> first, @NonNull PRCommand<T> second) {
        super();
        this.first = first;
        this.second = second;
    }

    protected @Override T _call() {
        first.await();
        second.setProgressListener(this.getProgressListener());
        return second.call();
    }
}
