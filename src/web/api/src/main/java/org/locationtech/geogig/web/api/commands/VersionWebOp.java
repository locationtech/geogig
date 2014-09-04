/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.porcelain.VersionInfo;
import org.locationtech.geogig.api.porcelain.VersionOp;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.ResponseWriter;

/**
 * Interface for the Version operation in the GeoGig.
 * 
 * Web interface for {@link VersionOp}, {@link VersionInfo}
 */

public class VersionWebOp extends AbstractWebAPICommand {

    /**
     * Runs the command and builds the appropriate response.
     * 
     * @param context - the context to use for this command
     */
    @Override
    public void run(CommandContext context) {
        final Context geogig = this.getCommandLocator(context);

        final VersionInfo info = geogig.command(VersionOp.class).call();

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
