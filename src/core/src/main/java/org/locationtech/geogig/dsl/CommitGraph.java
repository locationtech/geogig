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

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.storage.GraphDatabase;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

public @RequiredArgsConstructor class CommitGraph {
    private final @NonNull Context context;

    public GraphDatabase db() {
        return context.graphDatabase();
    }

    public ObjectId getMapping(@NonNull ObjectId objectId) {
        return db().getMapping(objectId);
    }

    public Optional<ObjectId> commonAncestor(@NonNull ObjectId left, @NonNull ObjectId right) {
        return context.command(FindCommonAncestor.class).setLeftId(left).setRightId(right).call();
    }

}
