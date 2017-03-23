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

import org.locationtech.geogig.porcelain.index.Index;
import org.locationtech.geogig.porcelain.index.UpdateIndexOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.restlet.data.Method;
import org.restlet.data.Status;

import com.vividsolutions.jts.geom.Envelope;

/**
 * The interface for the Update Index operation in GeoGig.
 * 
 * Web interface for {@link UpdateIndexOp}
 */

public class UpdateIndex extends AbstractWebAPICommand {

    String treeRefSpec;

    String geometryAttributeName;

    List<String> extraAttributes;

    boolean indexHistory;

    boolean overwrite;

    boolean add;

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
        setAdd(Boolean.valueOf(options.getFirstValue("add", "false")));
        setOverwrite(Boolean.valueOf(options.getFirstValue("overwrite", "false")));
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

    public void setAdd(boolean add) {
        this.add = add;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public void setBBox(String bbox) {
        this.bbox = bbox;
    }

    @Override
    public boolean supports(final Method method) {
        return Method.POST.equals(method);
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

        Envelope bounds = SpatialOps.parseNonReferencedBBOX(bbox);

        final Index index = repository.command(UpdateIndexOp.class)//
                .setTreeRefSpec(treeRefSpec)//
                .setAttributeName(geometryAttributeName)//
                .setExtraAttributes(extraAttributes)//
                .setIndexHistory(indexHistory)//
                .setAdd(add)//
                .setOverwrite(overwrite)//
                .setBounds(bounds)//
                .call();

        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeIndexInfo(index.info(), "index", false);
                out.writeElement("indexedTreeId", index.indexTreeId().toString());
                out.finish();
            }
        });
        setStatus(Status.SUCCESS_CREATED);
    }

}
