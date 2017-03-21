/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.web.api.commands;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Preconditions;

/**
 * Provides a way of getting object descriptions from GeoGig.
 * 
 * Note: This class does not use the internal CatObject implementation.
 */

public class Cat extends AbstractWebAPICommand {

    String object;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setObjectId(options.getRequiredValue("objectid"));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    /**
     * Mutator for the object variable
     * 
     * @param object - the object you want to view
     */
    public void setObjectId(String object) {
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
    protected void runInternal(CommandContext context) {
        ObjectId objectId = ObjectId.NULL;
        try {
            objectId = ObjectId.valueOf(object);
        } catch (Exception e) {
            // Do nothing, the argument will be checked.
        }
        Preconditions.checkArgument(!objectId.equals(ObjectId.NULL),
                "You must specify a valid non-null ObjectId.");
        final Context geogig = this.getRepositoryContext(context);

        Preconditions.checkArgument(geogig.objectDatabase().exists(objectId),
                "The specified ObjectId was not found in the respository.");
        final RevObject revObject = geogig.objectDatabase().get(objectId);
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
