/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.repository.WorkingTree;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Removes a feature or a tree from the working tree and index
 * 
 */
@CanRunDuringConflict
public class RemoveOp extends AbstractGeoGigOp<WorkingTree> {

    private List<String> pathsToRemove;

    public RemoveOp() {
        this.pathsToRemove = new ArrayList<String>();
    }

    /**
     * @param path a path to remove
     * @return {@code this}
     */
    public RemoveOp addPathToRemove(final String path) {
        pathsToRemove.add(path);
        return this;
    }

    /**
     * @see java.util.concurrent.Callable#call()
     */
    protected WorkingTree _call() {

        // Check that all paths are valid and exist
        for (String pathToRemove : pathsToRemove) {
            NodeRef.checkValidPath(pathToRemove);
            Optional<NodeRef> node;
            node = command(FindTreeChild.class).setParent(workingTree().getTree()).setIndex(true)
                    .setChildPath(pathToRemove).call();
            List<Conflict> conflicts = index().getConflicted(pathToRemove);
            if (conflicts.size() > 0) {
                for (Conflict conflict : conflicts) {
                    stagingDatabase().removeConflict(null, conflict.getPath());
                }
            } else {
                Preconditions.checkArgument(node.isPresent(),
                        "pathspec '%s' did not match any feature or tree", pathToRemove);
            }
        }

        // separate trees from features an delete accordingly
        for (String pathToRemove : pathsToRemove) {
            Optional<NodeRef> node = command(FindTreeChild.class)
                    .setParent(workingTree().getTree()).setIndex(true).setChildPath(pathToRemove)
                    .call();
            if (!node.isPresent()) {
                continue;
            }

            switch (node.get().getType()) {
            case TREE:
                workingTree().delete(pathToRemove);
                break;
            case FEATURE:
                String parentPath = NodeRef.parentPath(pathToRemove);
                String name = node.get().name();
                workingTree().delete(parentPath, name);
                break;
            default:
                break;
            }

            final long numChanges = workingTree().countUnstaged(pathToRemove).count();
            Iterator<DiffEntry> unstaged = workingTree().getUnstaged(pathToRemove);
            index().stage(getProgressListener(), unstaged, numChanges);
        }

        return workingTree();
    }

}
