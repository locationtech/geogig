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
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.locationtech.jts.geom.Envelope;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMethod;

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

    String bbox;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setTreeRefSpec(options.getRequiredValue("treeRefSpec"));
        setGeometryAttributeName(options.getFirstValue("geometryAttributeName", null));
        String[] extraAttributes = options.getValuesArray("extraAttributes");
        if (extraAttributes == null) {
            setExtraAttributes(null);
        } else {
            setExtraAttributes(Arrays.asList(extraAttributes));
        }
        setIndexHistory(Boolean.valueOf(options.getFirstValue("indexHistory", "false")));
        setBBox(options.getFirstValue("bounds", null));
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

    public void setBBox(String bbox) {
        this.bbox = bbox;
    }

    @Override
    public boolean supports(final RequestMethod method) {
        return RequestMethod.PUT.equals(method);
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

        Envelope bounds = SpatialOps.parseNonReferencedBBOX(bbox);

        final Index index = repository.command(CreateQuadTree.class)//
                .setTreeRefSpec(treeRefSpec)//
                .setGeometryAttributeName(geometryAttributeName)//
                .setExtraAttributes(extraAttributes)//
                .setIndexHistory(indexHistory)//
                .setBounds(bounds)//
                .call();

        context.setResponseContent(new CommandResponse() {
            @Override
            public HttpStatus getStatus() {
                return HttpStatus.CREATED;
            }

            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeIndexInfo(index.info(), "index", false);
                out.writeElement("indexedTreeId", index.indexTreeId().toString());
                out.finish();
            }
        });
    }

}
