/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.List;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.CheckRefFormat;
import org.locationtech.geogig.plumbing.ForEachRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

import lombok.extern.slf4j.Slf4j;

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
@Slf4j
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
    protected Ref _call() {
        checkArgument(newBranchName != null, "New branch name not specified");
        checkArgument(!newBranchName.equals(oldBranchName), "Done");
        command(CheckRefFormat.class).setThrowsException(true).setRef(newBranchName)
                .setAllowOneLevel(true).call();
        Optional<Ref> oldBranch = Optional.absent();

        if (oldBranchName == null) {
            Optional<Ref> headRef = command(RefParse.class).setName(Ref.HEAD).call();
            checkArgument(headRef.isPresent() && headRef.get() instanceof SymRef,
                    "Cannot rename detached HEAD.");
            oldBranch = command(RefParse.class).setName(headRef.get().peel().localName()).call();
        } else {
            oldBranch = command(RefParse.class).setName(oldBranchName).call();
        }

        checkState(oldBranch.isPresent(), "The branch could not be resolved.");

        Optional<Ref> newBranch = command(RefParse.class).setName(newBranchName).call();

        if (!force) {
            checkArgument(!newBranch.isPresent(), "Cannot rename branch to '" + newBranchName
                    + "' because a branch by that name already exists. Use force option to override this.");
        }

        Optional<Ref> renamedBranch = command(UpdateRef.class)
                .setName(oldBranch.get().namespace() + newBranchName)
                .setNewValue(oldBranch.get().getObjectId()).call();

        final BranchConfig oldConfig = command(BranchConfigOp.class)
                .setName(oldBranch.get().localName()).delete();
        final BranchConfig newConfig = command(BranchConfigOp.class)
                .setName(renamedBranch.get().localName())//
                .setRemoteName(oldConfig.getRemoteName().orElse(null))//
                .setRemoteBranch(oldConfig.getRemoteBranch().orElse(null))//
                .setDescription(oldConfig.getDescription().orElse(null))//
                .call();
        // update any sym refs that pointed to the old branch
        final Predicate<Ref> filter = new Predicate<Ref>() {
            @Override
            public boolean apply(Ref input) {
                if (input instanceof SymRef) {
                    return true;
                }
                return false;
            }
        };

        List<Ref> symRefs = Lists.newArrayList(command(ForEachRef.class).setFilter(filter).call());
        for (Ref ref : symRefs) {
            if (((SymRef) ref).getTarget().equals(oldBranch.get().getName())) {
                command(UpdateSymRef.class).setName(ref.getName())
                        .setNewValue(renamedBranch.get().getName()).call();
            }
        }

        // delete old ref
        command(UpdateRef.class).setName(oldBranch.get().getName()).setDelete(true).call();

        log.debug("Renamed branch {} -> {} {}", oldBranch.get(), renamedBranch.get(), newConfig);
        return renamedBranch.get();

    }
}
