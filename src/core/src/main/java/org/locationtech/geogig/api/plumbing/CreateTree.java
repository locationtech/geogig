/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.api.plumbing.diff.MutableTree;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StagingDatabase;

/**
 * Creates a new {@link RevTreeBuilder} backed by the specified object database (the repository's by
 * default, or the staging area object database if so indicated)
 */
public class CreateTree extends AbstractGeoGigOp<RevTreeBuilder> {

    private boolean index;

    /**
     * @param toIndexDb if {@code true}, the returned tree is backed by the {@link StagingDatabase},
     *        otherwise by the repository's {@link ObjectDatabase}
     * @return {@code this}
     */
    public CreateTree setIndex(boolean toIndexDb) {
        index = toIndexDb;
        return this;
    }

    /**
     * Executes the create tree operation and returns a new mutable tree.
     * 
     * @return the {@link MutableTree} that was created by the operation
     */
    @Override
    protected  RevTreeBuilder _call() {
        ObjectDatabase storage = index ? stagingDatabase() : objectDatabase();
        return new RevTreeBuilder(storage);
    }

}
