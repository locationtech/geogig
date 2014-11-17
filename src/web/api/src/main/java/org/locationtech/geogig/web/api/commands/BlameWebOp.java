/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.api.commands;

import javax.xml.stream.XMLStreamException;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.porcelain.BlameException;
import org.locationtech.geogig.api.porcelain.BlameOp;
import org.locationtech.geogig.api.porcelain.BlameReport;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Interface for the Blame operation in the GeoGig.
 * 
 * Web interface for {@link BlameOp}, {@link BlameReport}
 */

public class BlameWebOp extends AbstractWebAPICommand {

    private String path;

    private String branchOrCommit;

    /**
     * Mutator for the branchOrCommit variable
     * 
     * @param branchOrCommit - the branch or commit to blame from
     */
    public void setCommit(String branchOrCommit) {
        this.branchOrCommit = branchOrCommit;
    }

    /**
     * Mutator for the path variable
     * 
     * @param path - the path of the feature
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Runs the command and builds the appropriate response.
     * 
     * @param context - the context to use for this command
     */
    @Override
    public void run(CommandContext context) {
        final Context geogig = this.getCommandLocator(context);

        Optional<ObjectId> commit = Optional.absent();
        if (branchOrCommit != null) {
            commit = geogig.command(RevParse.class).setRefSpec(branchOrCommit).call();
            if (!commit.isPresent()) {
                throw new CommandSpecException("Could not resolve branch or commit");
            }
        }

        try {
            final BlameReport report = geogig.command(BlameOp.class).setPath(path)
                    .setCommit(commit.orNull()).call();

            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    try {
                        out.writeBlameReport(report);
                    } catch (XMLStreamException e) {
                        throw new CommandSpecException("Error writing stream.");
                    }
                    out.finish();
                }
            });
        } catch (BlameException e) {
            switch (e.statusCode) {
            case PATH_NOT_FEATURE:
                throw new CommandSpecException("The supplied path does not resolve to a feature");
            case FEATURE_NOT_FOUND:
                throw new CommandSpecException("The supplied path does not exist");
            }
        }
    }

}
