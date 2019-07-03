/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import org.locationtech.geogig.di.ContextImpl;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.ContextBuilder;
import org.locationtech.geogig.repository.DefaultPlatform;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;

import lombok.NonNull;

public class ContextBuilderImpl implements ContextBuilder {

    public @Override int getPriority() {
        return 0;
    }

    public @Override final Context build() {
        return build(new Hints());
    }

    public @Override Context build(@NonNull Hints hints) {
        Platform platform = hints.get(Hints.PLATFORM).filter(Platform.class::isInstance)
                .map(Platform.class::cast).orElseGet(() -> new DefaultPlatform());

        return new ContextImpl(platform, hints);
    }

}
