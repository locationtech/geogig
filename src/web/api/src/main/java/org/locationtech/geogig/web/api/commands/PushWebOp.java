/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.porcelain.PushOp;
import org.locationtech.geogig.api.porcelain.SynchronizationException;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.ResponseWriter;

/**
 * Interface for the Push operation in GeoGig.
 * 
 * Web interface for {@link PushOp}
 */
public class PushWebOp extends AbstractWebAPICommand {
    private String remoteName;

    private boolean pushAll;

    private String refSpec;

    /**
     * Mutator for the remoteName variable
     * 
     * @param remoteName - the name of the remote to push to
     */
    public void setRemoteName(String remoteName) {
        this.remoteName = remoteName;
    }

    /**
     * Mutator for the pushAll variable
     * 
     * @param pushAll - true to push all refs
     */
    public void setPushAll(boolean pushAll) {
        this.pushAll = pushAll;
    }

    /**
     * Mutator for the refSpec variable
     * 
     * @param refSpecs - the ref to push
     */
    public void setRefSpec(String refSpec) {
        this.refSpec = refSpec;
    }

    /**
     * Runs the command and builds the appropriate response.
     * 
     * @param context - the context to use for this command
     */
    @Override
    public void run(CommandContext context) {
        final Context geogig = this.getCommandLocator(context);

        PushOp command = geogig.command(PushOp.class);

        if (refSpec != null) {
            command.addRefSpec(refSpec);
        }

        try {
            final Boolean dataPushed = command.setAll(pushAll).setRemote(remoteName).call();
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeElement("Push", "Success");
                    out.writeElement("dataPushed", dataPushed.toString());
                    out.finish();
                }
            });
        } catch (SynchronizationException e) {
            switch (e.statusCode) {
            case REMOTE_HAS_CHANGES:
                context.setResponseContent(CommandResponse
                        .error("Push failed: The remote repository has changes that would be lost in the event of a push."));
                break;
            case HISTORY_TOO_SHALLOW:
                context.setResponseContent(CommandResponse
                        .error("Push failed: There is not enough local history to complete the push."));
            default:
                break;
            }
        }
    }

}
