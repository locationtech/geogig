/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Kelsey Ishmael (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import java.util.List;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.porcelain.BranchCreateOp;
import org.locationtech.geogig.api.porcelain.BranchListOp;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.collect.Lists;

/**
 * The interface for the Branch operations in GeoGig. Currently only supports listing of local and
 * remote branches.
 * 
 * Web interface for {@link BranchListOp}
 */

public class BranchWebOp extends AbstractWebAPICommand {

    boolean list;

    boolean remotes;

    /**
     * Branch name to create.
     */
    String branchName;

    /**
     * Force create. Will overwrite an existing branch, if it already exists.
     */
    boolean force;

    /**
     * Automatically checkout created branch after creation.
     */
    boolean autoCheckout;

    /**
     * Orphan history. Setting this will ignore the history of the branch from which a new branch is
     * created.
     */
    boolean orphan;

    /**
     * Mutator for the list option
     * 
     * @param list - true if you want to list any branches
     */
    public void setList(boolean list) {
        this.list = list;
    }

    /**
     * Mutator for the remote option
     * 
     * @param remotes - true if you want to list remote branches
     */
    public void setRemotes(boolean remotes) {
        this.remotes = remotes;
    }

    /**
     * Mutator for the Branch Name.
     *
     * @param branchName The name of the Branch to create.
     */
    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    /**
     * Mutator for the Force option.
     *
     * @param createForce - true if branch creation should overwrite an existing branch with the
     * same Branch Name.
     */
    public void setCreateForce(boolean createForce) {
        this.force = createForce;
    }

    /**
     * Mutator for AutoCheckout.
     *
     * @param autoCheckout - true if the working branch should switch/checkout the newly created
     * branch.
     */
    public void setAutoCheckout(boolean autoCheckout) {
        this.autoCheckout = autoCheckout;
    }

    /**
     * Mutator for Orphan.
     *
     * @param orphan - true is the newly created branch should NOT inherit the history of the branch
     * it was created from.
     */
    public void setOrphan(boolean orphan) {
        this.orphan = orphan;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     */
    @Override
    public void run(CommandContext context) {
        if (list) {
            final Context geogig = this.getCommandLocator(context);
            final List<Ref> localBranches = geogig.command(BranchListOp.class).call();
            final List<Ref> remoteBranches;
            if (remotes) {
                remoteBranches = geogig.command(BranchListOp.class).setLocal(false)
                        .setRemotes(remotes).call();
            } else {
                remoteBranches = Lists.newLinkedList();
            }
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeBranchListResponse(localBranches, remoteBranches);
                    out.finish();
                }
            });
        } else if (branchName != null) {
            // branchName provided, must be a create branch request
            final Context geogig = this.getCommandLocator(context);
            Ref createdBranch = geogig.command(BranchCreateOp.class).setName(branchName)
                    .setAutoCheckout(autoCheckout).setForce(force).setOrphan(orphan).call();
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeBranchCreateResponse(createdBranch);
                    out.finish();
                }
            });
        }
    }

}
