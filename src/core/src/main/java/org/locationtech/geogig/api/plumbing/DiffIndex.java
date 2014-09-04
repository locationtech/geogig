/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;

/**
 * Compares content and metadata links of blobs between the index and repository
 */
public class DiffIndex extends AbstractGeoGigOp<Iterator<DiffEntry>> implements
        Supplier<Iterator<DiffEntry>> {

    private String refSpec;

    private final List<String> pathFilters = Lists.newLinkedList();

    private boolean reportTrees;

    /**
     * @param pathFilter the path filter to use during the diff operation
     * @return {@code this}
     */
    public DiffIndex setFilter(@Nullable List<String> pathFilter) {
        this.pathFilters.clear();
        if (pathFilter != null) {
            this.pathFilters.addAll(pathFilter);
        }
        return this;
    }

    public DiffIndex addFilter(@Nullable String pathFilter) {
        if (pathFilter != null) {
            this.pathFilters.add(pathFilter);
        }
        return this;
    }

    /**
     * @param refSpec the name of the root tree object in the repository's object database to
     *        compare the index against. If {@code null} or not specified, defaults to the tree
     *        object of the current HEAD commit.
     * @return {@code this}
     */
    public DiffIndex setOldVersion(@Nullable String refSpec) {
        this.refSpec = refSpec;
        return this;
    }

    /**
     * Finds differences between the tree pointed to by the given ref and the index.
     * 
     * @return an iterator to a set of differences between the two trees
     * @see DiffEntry
     */
    @Override
    protected Iterator<DiffEntry> _call() {
        final String oldVersion = Optional.fromNullable(refSpec).or(Ref.HEAD);
        final Optional<ObjectId> rootTreeId;
        rootTreeId = command(ResolveTreeish.class).setTreeish(oldVersion).call();
        Preconditions.checkArgument(rootTreeId.isPresent(), "refSpec %s did not resolve to a tree",
                oldVersion);

        final RevTree rootTree;

        rootTree = command(RevObjectParse.class).setObjectId(rootTreeId.get()).call(RevTree.class)
                .get();

        final RevTree newTree = index().getTree();

        DiffTree diff = command(DiffTree.class).setPathFilter(this.pathFilters)
                .setReportTrees(this.reportTrees).setOldTree(rootTree.getId())
                .setNewTree(newTree.getId());

        return diff.call();
    }

    /**
     * @param reportTrees
     * @return
     */
    public DiffIndex setReportTrees(boolean reportTrees) {
        this.reportTrees = reportTrees;
        return this;
    }

    /**
     * Implements {@link Supplier#get()} by deferring to {@link #call()}
     * 
     * @see #call()
     */
    @Override
    public Iterator<DiffEntry> get() {
        return call();
    }
}
