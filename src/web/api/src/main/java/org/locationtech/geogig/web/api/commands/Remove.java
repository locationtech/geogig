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

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.porcelain.RemoveOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

/**
 * Interface for the Remove operation in GeoGig.
 * 
 * Web interface for {@link RemoveOp}
 */

public class Remove extends AbstractWebAPICommand {

    String path;

    boolean recursive;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setPath(options.getRequiredValue("path"));
        setRecursive(Boolean.valueOf(options.getFirstValue("recursive", "false")));
    }

    /**
     * Mutator for the path variable
     * 
     * @param path - the path to the feature to be removed
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Mutator for the recursive variable
     * 
     * @param recursive - true to remove a tree and all features under it
     */
    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    /**
     * Runs the command and builds the appropriate response.
     * 
     * @param context - the context to use for this command
     * 
     * @throws CommandSpecException
     */
    @Override
    protected void runInternal(CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);

        NodeRef.checkValidPath(path);

        RemoveOp command = geogig.command(RemoveOp.class).setRecursive(recursive);

        command.addPathToRemove(path).call();
        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeElement("Deleted", path);
                out.finish();
            }
        });
    }

}
