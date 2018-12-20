/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static com.google.common.collect.Iterators.filter;
import static com.google.common.collect.Iterators.transform;

import java.util.Iterator;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.plumbing.DiffWorkTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;

/**
 * Removes untracked features from the working tree
 * 
 */
@CanRunDuringConflict
public class CleanOp extends AbstractGeoGigOp<WorkingTree> {

    private String path;

    /**
     * @see java.util.concurrent.Callable#call()
     */
    protected WorkingTree _call() {

        if (path != null) {
            // check that is a valid path
            NodeRef.checkValidPath(path);

            Optional<NodeRef> ref = command(FindTreeChild.class).setParent(workingTree().getTree())
                    .setChildPath(path).call();

            Preconditions.checkArgument(ref.isPresent(), "pathspec '%s' did not match any tree",
                    path);
            Preconditions.checkArgument(ref.get().getType() == TYPE.TREE,
                    "pathspec '%s' did not resolve to a tree", path);
        }

        try (final AutoCloseableIterator<DiffEntry> unstaged = command(DiffWorkTree.class)
                .setFilter(path).call()) {
            final Iterator<DiffEntry> added = filter(unstaged, new Predicate<DiffEntry>() {

                @Override
                public boolean apply(@Nullable DiffEntry input) {
                    return input.changeType().equals(ChangeType.ADDED);
                }
            });

            //(de) -> de.newPath(), but friendly for Fortify
            Function<DiffEntry, String> fn_DiffEntry_newPath =  new Function<DiffEntry, String>() {
                @Override
                public String apply(DiffEntry node) {
                    return node.newPath();
                }};

            workingTree().delete(transform(added,fn_DiffEntry_newPath), getProgressListener());
        }

        return workingTree();

    }

    /**
     * @param path a path to clean
     * @return {@code this}
     */
    public CleanOp setPath(final String path) {
        this.path = path;
        return this;
    }

}
