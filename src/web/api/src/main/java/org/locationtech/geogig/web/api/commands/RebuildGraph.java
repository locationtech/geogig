/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.RebuildGraphOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.collect.ImmutableList;

/**
 * Interface for the rebuild graph operation in GeoGig.
 * 
 * Web interface for {@link RebuildGraphOp}
 */

public class RebuildGraph extends AbstractWebAPICommand {

    boolean quiet = false;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setQuiet(Boolean.valueOf(options.getFirstValue("quiet", "false")));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

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
    protected void runInternal(CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);

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
