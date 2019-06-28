/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.dsl;

import java.util.Optional;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.repository.Context;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public @RequiredArgsConstructor class Refs {
    private final @NonNull Context context;

    public Optional<Ref> head() {
        return get(Ref.HEAD);
    }

    public Optional<Ref> get(@NonNull String name) {
        return context.command(RefParse.class).setName(name).call();
    }
}
