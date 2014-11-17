/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.api.commands;

import java.util.List;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.Ref;
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

    private boolean list;

    private boolean remotes;

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
        }
    }

}
