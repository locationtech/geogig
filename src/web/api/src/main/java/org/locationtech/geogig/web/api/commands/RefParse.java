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

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.repository.Context;
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

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setName(options.getRequiredValue("name"));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
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
        final Context geogig = this.getRepositoryContext(context);
        Optional<Ref> ref = geogig.command(org.locationtech.geogig.plumbing.RefParse.class)
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
