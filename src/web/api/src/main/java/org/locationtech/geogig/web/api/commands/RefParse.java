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

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * Interface for the RefParse command in GeoGig
 * 
 * Web interface for {@link RefParse}
 */

public class RefParse extends AbstractWebAPICommand {

    String refSpec;

    public RefParse(ParameterSet options) {
        super(options);
        setName(options.getFirstValue("name", null));
    }

    /**
     * Mutator for the refSpec variable
     * 
     * @param name - the refSpec to parse
     */
    public void setName(String name) {
        this.refSpec = name;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     * 
     * @throws CommandSpecException
     */
    @Override
    protected void runInternal(CommandContext context) {
        if (refSpec == null) {
            throw new CommandSpecException("No name was given.");
        }

        final Context geogig = this.getCommandLocator(context);
        Optional<Ref> ref = geogig.command(org.locationtech.geogig.api.plumbing.RefParse.class)
                    .setName(refSpec).call();

        if (!ref.isPresent()) {
            throw new CommandSpecException("Unable to parse the provided name.");
        }

        final Ref newRef = ref.get();
        context.setResponseContent(new CommandResponse() {

            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeRefParseResponse(newRef);
                out.finish();
            }
        });
    }

}
