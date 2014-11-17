/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.porcelain;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;

import com.google.common.base.Optional;

/**
 * Deletes a branch by deleting its reference.
 * <p>
 * If trying to delete the current branch (i.e. HEAD points to that same branch), the operation
 * fails.
 */
public class BranchDeleteOp extends AbstractGeoGigOp<Optional<? extends Ref>> {

    private String branchName;

    /**
     * @param branchName the name of the branch to delete, in a form {@link RefParse} understands.
     *        Must resolve to a branch reference.
     * @return {@code this}
     */
    public BranchDeleteOp setName(final String branchName) {
        this.branchName = branchName;
        return this;
    }

    /**
     * @return the reference to the branch deleted, or absent if no such branch existed
     * @throws RuntimeException if the branch couldn't be deleted
     * @Throws IllegalStateException if the branch to be deleted is the HEAD
     * @throws IllegalArgumentException if the given branch name does not resolve to a branch
     *         reference (i.e. under the {@link Ref#HEADS_PREFIX heads} or
     *         {@link Ref#REMOTES_PREFIX remotes} namespace)
     */
    @Override
    protected  Optional<? extends Ref> _call() {
        checkState(branchName != null, "Branch name not provided");
        Optional<Ref> branchRef = command(RefParse.class).setName(branchName).call();
        if (branchRef.isPresent()) {
            final Ref ref = branchRef.get();
            checkArgument(
                    ref.getName().startsWith(Ref.HEADS_PREFIX)
                            || ref.getName().startsWith(Ref.REMOTES_PREFIX), branchName
                            + " does not resolve to a branch reference: " + ref.getName());
            checkState(!(ref instanceof SymRef));

            final Optional<Ref> head = command(RefParse.class).setName(Ref.HEAD).call();
            checkState(!(head.isPresent() && head.get() instanceof SymRef && ((SymRef) head.get())
                    .getTarget().equals(ref.getName())), "Cannot delete the branch you are on");

            UpdateRef updateRef = command(UpdateRef.class).setName(ref.getName()).setDelete(true)
                    .setReason("Delete branch " + ref.getName());
            branchRef = updateRef.call();
            checkState(branchRef.isPresent());
        }
        return branchRef;
    }

}
