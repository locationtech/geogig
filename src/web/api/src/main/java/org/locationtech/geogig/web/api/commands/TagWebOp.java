/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Kelsey Ishmael (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import java.util.List;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.porcelain.TagListOp;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.ResponseWriter;

/**
 * Interface for the Tag operations in GeoGig. Currently only supports the list option.
 * 
 * Web interface for {@link TagListOp}
 */

public class TagWebOp extends AbstractWebAPICommand {

    private boolean list;

    /**
     * Mutator for the list variable
     * 
     * @param list - true to list the names of your tags
     */
    public void setList(boolean list) {
        this.list = list;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     */
    @Override
    public void run(CommandContext context) {
        if (list) {
            final Context geogig = this.getCommandLocator(context);
            final List<RevTag> tags = geogig.command(TagListOp.class).call();

            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeTagListResponse(tags);
                    out.finish();
                }
            });
        }
    }

}
