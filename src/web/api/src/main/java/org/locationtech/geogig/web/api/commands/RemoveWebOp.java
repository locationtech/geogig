/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.porcelain.RemoveOp;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Interface for the Remove operation in GeoGig.
 * 
 * Web interface for {@link RemoveOp}
 */

public class RemoveWebOp extends AbstractWebAPICommand {

    private String path;

    private boolean recursive;

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
    public void run(CommandContext context) {
        if (this.getTransactionId() == null) {
            throw new CommandSpecException(
                    "No transaction was specified, remove requires a transaction to preserve the stability of the repository.");
        }
        if (this.path == null) {
            throw new CommandSpecException("No path was specified for removal.");
        }

        final Context geogig = this.getCommandLocator(context);
        RemoveOp command = geogig.command(RemoveOp.class);

        NodeRef.checkValidPath(path);

        Optional<NodeRef> node = geogig.command(FindTreeChild.class)
                .setParent(geogig.workingTree().getTree()).setIndex(true).setChildPath(path)
                .call();
        if (node.isPresent()) {
            NodeRef nodeRef = node.get();
            if (nodeRef.getType() == TYPE.TREE) {
                if (!recursive) {
                    throw new CommandSpecException(
                            "Recursive option must be used to remove a tree.");
                }
            }
        }

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
