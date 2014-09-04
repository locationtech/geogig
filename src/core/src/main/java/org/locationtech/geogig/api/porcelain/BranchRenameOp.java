/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import static com.google.common.base.Preconditions.checkState;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;

import com.google.common.base.Optional;

/**
 * Renames a branch by updating its reference
 * <p>
 * If trying to rename the current branch you can not give an oldBranchName and it will rename the
 * current branch to the given newBranchName. If you are trying to rename a branch to a name that is
 * already a branch name you cannot rename it, unless the {@link #setForce(boolean) force} is set to
 * {@code true} in which case it deletes the branch that exists with that name already and renames
 * the other branch to that name.
 * 
 */
public class BranchRenameOp extends AbstractGeoGigOp<Ref> {

    private boolean force;

    private String newBranchName;

    private String oldBranchName;

    /**
     * @param force whether to force the renaming of a branch to a branch name that already exists
     * @return {@code this}
     */
    public BranchRenameOp setForce(boolean force) {
        this.force = force;
        return this;
    }

    /**
     * @param oldBranchName the name of the branch that you want to change, if null it uses the
     *        current branch, if not null must resolve to a branch reference
     * @return {@code this}
     */
    public BranchRenameOp setOldName(String oldBranchName) {
        this.oldBranchName = oldBranchName;
        return this;
    }

    /**
     * @param newBranchName the new name that you want to assign to the branch
     * @return {@code this}
     */
    public BranchRenameOp setNewName(String newBranchName) {
        this.newBranchName = newBranchName;
        return this;
    }

    /**
     * @return The newly renamed branch
     * @throws IllegalStateException if newBranchName and oldBranchName are the same or if you try
     *         to rename a branch with a name that already exists and you don't use force
     * @throws IllegalArgumentException if newBranchName is not specified or if the oldBranchName
     *         specified doesn't exist or if oldBranchName doesn't resolve to a branch
     */
    @Override
    protected  Ref _call() {
        checkState(newBranchName != null, "New branch name not specified");
        checkState(!newBranchName.equals(oldBranchName), "Done");
        Optional<Ref> branch = Optional.absent();

        boolean headBranch = false;

        if (oldBranchName == null) {
            Optional<Ref> headRef = command(RefParse.class).setName(Ref.HEAD).call();
            checkState(headRef.isPresent() && headRef.get() instanceof SymRef,
                    "Cannot rename detached HEAD.");
            branch = command(RefParse.class).setName(((SymRef) (headRef.get())).getTarget()).call();
            headBranch = true;
        } else {
            branch = command(RefParse.class).setName(oldBranchName).call();
        }

        checkState(branch.isPresent(), "The branch could not be resolved.");

        Optional<Ref> newBranch = command(RefParse.class).setName(newBranchName).call();

        if (!force) {
            checkState(
                    !newBranch.isPresent(),
                    "Cannot rename branch to '"
                            + newBranchName
                            + "' because a branch by that name already exists. Use force option to override this.");
        }

        Optional<Ref> renamedBranch = command(UpdateRef.class)
                .setName(branch.get().namespace() + newBranchName)
                .setNewValue(branch.get().getObjectId()).call();

        command(UpdateRef.class).setName(branch.get().getName()).setDelete(true).call();

        if (headBranch) {
            command(UpdateSymRef.class).setName(Ref.HEAD)
                    .setNewValue(renamedBranch.get().getName()).call();
        }

        return renamedBranch.get();

    }
}
