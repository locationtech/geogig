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
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.ResolveTree;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.storage.ObjectStore;

import lombok.Getter;
import lombok.NonNull;

class ObjectStores {

    protected final @Getter Context context;

    protected final ObjectStore store;

    protected ObjectStores(@NonNull Context context, @NonNull ObjectStore store) {
        this.context = context;
        this.store = store;
    }

    public ObjectStore store() {
        return store;
    }

    public boolean exists(@NonNull ObjectId id) {
        return store().exists(id);
    }

    public boolean put(@NonNull RevObject object) {
        return store().put(object);
    }

    public @NonNull RevObject get(@NonNull ObjectId objectId) {
        return store().get(objectId);
    }

    public @NonNull Optional<RevObject> tryGet(@NonNull ObjectId objectId) {
        return Optional.ofNullable(store().getIfPresent(objectId));
    }

    public TreeWorker tree(@NonNull String treeIsh) {
        return new TreeWorker(context, store,
                context.command(ResolveTree.class).setSource(store).setTreeIsh(treeIsh).call());
    }

    public TreeWorker tree(@NonNull ObjectId treeIsh) {
        return new TreeWorker(context, store,
                context.command(ResolveTree.class).setSource(store).setTreeIsh(treeIsh).call());
    }

    public TreeWorker tree(@NonNull RevTree tree) {
        return new TreeWorker(context, store, Optional.of(tree));
    }

}
