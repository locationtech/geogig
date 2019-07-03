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

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.storage.ObjectStore;

import lombok.Getter;
import lombok.NonNull;

public class TreeWorker {

    private @NonNull @Getter Context context;

    private @NonNull ObjectStore store;

    private @Getter @NonNull Optional<RevTree> tree;

    TreeWorker(@NonNull Context context, @NonNull ObjectStore store,
            @NonNull Optional<RevTree> tree) {
        this.context = context;
        this.store = store;
        this.tree = tree;
    }

    public boolean exists() {
        return tree.isPresent();
    }

    public Optional<ObjectId> getId() {
        return tree.map(RevTree::getId);
    }

    /**
     * Search the given tree for the child path.
     * 
     * @param childPath the path to search for
     * @return an {@link Optional} of the {@link Node} for the child path, or
     *         {@link Optional#empty()} if it wasn't found
     */
    public Optional<Node> child(@NonNull String childPath) {
        return context.command(FindTreeChild.class).setSource(store)
                .setParent(tree.orElse(RevTree.EMPTY)).setChildPath(childPath).call()
                .map(NodeRef::getNode);
    }
}
