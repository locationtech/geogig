/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ResponseWriter;

/**
 * The interface for the Add operation in GeoGig.
 * 
 * Web interface for {@link AddOp}
 */

public class AddWebOp extends AbstractWebAPICommand {

    private String path;

    /**
     * Mutator for the path variable
     * 
     * @param path - the path to the feature you want to add
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     * 
     * @throws CommandSpecException
     */
    @Override
    public void run(CommandContext context) {
        if (this.getTransactionId() == null) {
            throw new CommandSpecException(
                    "No transaction was specified, add requires a transaction to preserve the stability of the repository.");
        }
        final Context geogig = this.getCommandLocator(context);

        AddOp command = geogig.command(AddOp.class);

        if (path != null) {
            command.addPattern(path);
        }

        command.call();
        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeElement("Add", "Success");
                out.finish();
            }
        });
    }

}
