/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.porcelain;

import java.util.Iterator;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.plumbing.DiffWorkTree;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry.ChangeType;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.repository.WorkingTree;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;

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
    protected  WorkingTree _call() {

        if (path != null) {
            // check that is a valid path
            NodeRef.checkValidPath(path);

            Optional<NodeRef> ref = command(FindTreeChild.class).setParent(workingTree().getTree())
                    .setChildPath(path).setIndex(true).call();

            Preconditions.checkArgument(ref.isPresent(), "pathspec '%s' did not match any tree",
                    path);
            Preconditions.checkArgument(ref.get().getType() == TYPE.TREE,
                    "pathspec '%s' did not resolve to a tree", path);
        }

        final Iterator<DiffEntry> unstaged = command(DiffWorkTree.class).setFilter(path).call();
        final Iterator<DiffEntry> added = Iterators.filter(unstaged, new Predicate<DiffEntry>() {

            @Override
            public boolean apply(@Nullable DiffEntry input) {
                return input.changeType().equals(ChangeType.ADDED);
            }
        });
        Iterator<String> addedPaths = Iterators.transform(added, new Function<DiffEntry, String>() {

            @Override
            public String apply(DiffEntry input) {
                return input.newPath();
            }
        });

        workingTree().delete(addedPaths);

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
