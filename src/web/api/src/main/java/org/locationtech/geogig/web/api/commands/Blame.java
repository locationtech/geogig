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
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.BlameException;
import org.locationtech.geogig.porcelain.BlameOp;
import org.locationtech.geogig.porcelain.BlameReport;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Interface for the Blame operation in the GeoGig.
 * 
 * Web interface for {@link BlameOp}, {@link BlameReport}
 */

public class Blame extends AbstractWebAPICommand {

    String path;

    String branchOrCommit;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setCommit(options.getFirstValue("commit", null));
        setPath(options.getRequiredValue("path"));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

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
    protected void runInternal(CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);

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
                    out.writeBlameReport(report);
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
