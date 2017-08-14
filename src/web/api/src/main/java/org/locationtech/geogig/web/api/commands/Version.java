/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.porcelain.VersionInfo;
import org.locationtech.geogig.porcelain.VersionOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.springframework.http.HttpStatus;

/**
 * Interface for the Version operation in the GeoGig.
 * 
 * Web interface for {@link VersionOp}, {@link VersionInfo}
 */

public class Version extends AbstractWebAPICommand {

    @Override
    protected void setParametersInternal(ParameterSet options) {
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    /**
     * Runs the command and builds the appropriate response.
     * 
     * @param context - the context to use for this command
     */
    @Override
    protected void runInternal(CommandContext context) {
        final Context geogig = this.getRepositoryContext(context);

        final VersionInfo info = geogig.command(VersionOp.class).call();

        if (info == null) {
            throw new CommandSpecException("No version information available.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeElement("ProjectVersion", info.getProjectVersion());
                out.writeElement("BuildTime", info.getBuildTime());
                out.writeElement("BuildUserName", info.getBuildUserName());
                out.writeElement("BuildUserEmail", info.getBuildUserEmail());
                out.writeElement("GitBranch", info.getBranch());
                out.writeElement("GitCommitID", info.getCommitId());
                out.writeElement("GitCommitTime", info.getCommitTime());
                out.writeElement("GitCommitAuthorName", info.getCommitUserName());
                out.writeElement("GitCommitAuthorEmail", info.getCommitUserEmail());
                out.writeElement("GitCommitMessage", info.getCommitMessageFull());
                out.finish();
            }
        });
    }

}
