/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Preconditions;

/**
 * Provides a way of getting object descriptions from GeoGig.
 * 
 * Note: This class does not use the internal CatObject implementation.
 */

public class CatWebOp extends AbstractWebAPICommand {

    private ObjectId object;

    /**
     * Mutator for the object variable
     * 
     * @param object - the object you want to view
     */
    public void setObjectId(ObjectId object) {
        this.object = object;
    }

    /**
     * Runs the command and builds the appropriate response
     * 
     * @param context - the context to use for this command
     * 
     * @throws CommandSpecException
     */
    @Override
    public void run(CommandContext context) {
        Preconditions.checkArgument(object != null && !object.equals(ObjectId.NULL));
        final Context geogig = this.getCommandLocator(context);

        Preconditions.checkState(geogig.stagingDatabase().exists(object));
        final RevObject revObject = geogig.stagingDatabase().get(object);
        switch (revObject.getType()) {
        case COMMIT:
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeCommit((RevCommit) revObject, "commit", null, null, null);
                    out.finish();
                }
            });
            break;
        case TREE:
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeTree((RevTree) revObject, "tree");
                    out.finish();
                }
            });
            break;
        case FEATURE:
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeFeature((RevFeature) revObject, "feature");
                    out.finish();
                }
            });
            break;
        case FEATURETYPE:
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeFeatureType((RevFeatureType) revObject, "featuretype");
                    out.finish();
                }
            });
            break;
        case TAG:
            context.setResponseContent(new CommandResponse() {
                @Override
                public void write(ResponseWriter out) throws Exception {
                    out.start();
                    out.writeTag((RevTag) revObject, "tag");
                    out.finish();
                }
            });
            break;
        }
    }

}
