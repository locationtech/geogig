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

import java.util.Arrays;
import java.util.List;

import org.locationtech.geogig.porcelain.index.CreateQuadTree;
import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.restlet.data.Method;
import org.restlet.data.Status;

/**
 * The interface for the Create Quad Tree operation in GeoGig.
 * 
 * Web interface for {@link CreateQuadTree}
 */

public class CreateIndex extends AbstractWebAPICommand {

    String treeRefSpec;

    String geometryAttributeName;

    List<String> extraAttributes;

    boolean indexHistory;

    public CreateIndex(ParameterSet options) {
        super(options);
        setTreeRefSpec(options.getFirstValue("treeRefSpec", null));
        setGeometryAttributeName(options.getFirstValue("geometryAttributeName", null));
        String[] extraAttributes = options.getValuesArray("path");
        if (extraAttributes == null) {
            setExtraAttributes(null);
        } else {
            setExtraAttributes(Arrays.asList(extraAttributes));
        }
        setIndexHistory(Boolean.valueOf(options.getFirstValue("indexHistory", "false")));
    }

    public void setTreeRefSpec(String treeRefSpec) {
        this.treeRefSpec = treeRefSpec;
    }

    public void setGeometryAttributeName(String geometryAttributeName) {
        this.geometryAttributeName = geometryAttributeName;
    }

    public void setExtraAttributes(List<String> extraAttributes) {
        this.extraAttributes = extraAttributes;
    }

    public void setIndexHistory(boolean indexHistory) {
        this.indexHistory = indexHistory;
    }

    @Override
    public boolean supports(final Method method) {
        return Method.PUT.equals(method);
    }

    @Override
    protected boolean requiresOpenRepo() {
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
        final Index index = repository.command(CreateQuadTree.class)//
                .setTreeRefSpec(treeRefSpec)//
                .setGeometryAttributeName(geometryAttributeName)//
                .setExtraAttributes(extraAttributes)//
                .setIndexHistory(indexHistory)//
                .call();

        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeTree(index.indexTree(), "tree");
                out.finish();
            }
        });
        setStatus(Status.SUCCESS_CREATED);
    }

}
