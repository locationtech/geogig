/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.web.api.index;

import org.locationtech.geogig.porcelain.index.DropIndexOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * The interface for the Drop Index operation in GeoGig.
 * 
 * Web interface for {@link DropIndexOp}
 */

public class DropIndex extends AbstractWebAPICommand {

    String treeRefSpec;

    String geometryAttributeName;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setTreeRefSpec(options.getRequiredValue("treeRefSpec"));
        setGeometryAttributeName(options.getFirstValue("geometryAttributeName", null));
    }

    public void setTreeRefSpec(String treeRefSpec) {
        this.treeRefSpec = treeRefSpec;
    }

    public void setGeometryAttributeName(String geometryAttributeName) {
        this.geometryAttributeName = geometryAttributeName;
    }

    @Override
    public boolean supports(final RequestMethod method) {
        return RequestMethod.DELETE.equals(method);
    }

    @Override
    public boolean requiresOpenRepo() {
        return true;
    }

    @Override
    public boolean requiresTransaction() {
        return false;
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
        Repository repository = context.getRepository();

        final IndexInfo dropped = repository.command(DropIndexOp.class)//
                .setTreeRefSpec(treeRefSpec)//
                .setAttributeName(geometryAttributeName)//
                .call();

        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeIndexInfo(dropped, "dropped", false);
                out.finish();
            }
        });
    }

}
