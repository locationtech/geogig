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

import static org.locationtech.geogig.base.Preconditions.checkArgument;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.CheckRefFormat;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;

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
    protected @Override Ref _call() {
        checkArgument(newBranchName != null, "New branch name not specified");
        checkArgument(!newBranchName.equals(oldBranchName), "Done");
        command(CheckRefFormat.class).setThrowsException(true).setRef(newBranchName)
                .setAllowOneLevel(true).call();

        final Ref oldBranchRef;

        if (oldBranchName == null) {
            oldBranchRef = command(RefParse.class).setName(Ref.HEAD).call()//
                    .filter(SymRef.class::isInstance)//
                    .map(Ref::peel)//
                    .orElseThrow(
                            () -> new IllegalArgumentException("Cannot rename detached HEAD."));
        } else {
            oldBranchRef = command(RefParse.class).setName(oldBranchName).call()//
                    .orElseThrow(() -> new IllegalArgumentException(
                            "The branch could not be resolved."));
        }
        if (!force && command(RefParse.class).setName(newBranchName).call().isPresent()) {
            throw new IllegalArgumentException("Cannot rename branch to '" + newBranchName
                    + "' because a branch by that name already exists. Use force option to override this.");
        }

        final String reason = String.format("branch-rename: %s to %s", oldBranchRef.localName(),
                newBranchName);
        UpdateRefs updateRefs = command(UpdateRefs.class).setReason(reason);

        final String newRefName = Ref.append(oldBranchRef.namespace(), newBranchName);
        final Ref newBranchRef = new Ref(newRefName, oldBranchRef.getObjectId());
        updateRefs.add(newBranchRef);
        updateRefs.remove(oldBranchRef.getName());

        // update any sym refs that pointed to the old branch
        refDatabase().getAll().stream()//
                .filter(SymRef.class::isInstance)//
                .map(SymRef.class::cast)//
                .filter(symRef -> symRef.getTarget().equals(oldBranchRef.getName()))//
                .map(symRef -> new SymRef(symRef.getName(), newBranchRef))//
                .forEach(updateRefs::add);

        final BranchConfig oldConfig = command(BranchConfigOp.class)
                .setName(oldBranchRef.localName()).delete();

        updateRefs.call();

        final BranchConfig newConfig = command(BranchConfigOp.class)
                .setName(newBranchRef.localName())//
                .setRemoteName(oldConfig.getRemoteName().orElse(null))//
                .setRemoteBranch(oldConfig.getRemoteBranch().orElse(null))//
                .setDescription(oldConfig.getDescription().orElse(null))//
                .call();

        log.debug("Renamed branch {} -> {} {}", oldBranchRef, newBranchRef, newConfig);
        return newBranchRef;

    }
}
