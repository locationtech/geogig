/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.plumbing.RebuildGraphOp;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.collect.ImmutableList;

/**
 * Interface for the rebuild graph operation in GeoGig.
 * 
 * Web interface for {@link RebuildGraphOp}
 */

public class RebuildGraphWebOp extends AbstractWebAPICommand {

    private boolean quiet = false;

    /**
     * Mutator for the quiet variable
     * 
     * @param quiet - If true, limit the output of the command.
     */
    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    /**
     * Runs the command and builds the appropriate response.
     * 
     * @param context - the context to use for this command
     */
    @Override
    public void run(CommandContext context) {
        final Context geogig = this.getCommandLocator(context);

        final ImmutableList<ObjectId> updatedObjects = geogig.command(RebuildGraphOp.class).call();

        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeRebuildGraphResponse(updatedObjects, quiet);
                out.finish();
            }
        });
    }
}
