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

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;

import com.google.common.base.Optional;

/**
 * This is the interface for the FeatureDiff command. It is used by passing a path to a feature, an
 * oldCommitId and a newCommitId. It returns the differences in the attributes of a feature between
 * the two supplied commits.
 * 
 * Web interface for {@link FeatureDiff}
 */

public class FeatureDiff extends AbstractWebAPICommand {

    String path;

    String newTreeish;

    String oldTreeish;

    boolean all;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setPath(options.getRequiredValue("path"));
        setOldTreeish(options.getFirstValue("oldTreeish", ObjectId.NULL.toString()));
        setNewTreeish(options.getFirstValue("newTreeish", ObjectId.NULL.toString()));
        setAll(Boolean.valueOf(options.getFirstValue("all", "false")));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    /**
     * Mutator of the path variable
     * 
     * @param path - the path to the feature
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Mutator for the newTreeish
     * 
     * @param newTreeish - the id of the newer commit
     */
    public void setNewTreeish(String newTreeish) {
        this.newTreeish = newTreeish;
    }

    /**
     * Mutator for the oldTreeish
     * 
     * @param oldTreeish - the id of the older commit
     */
    public void setOldTreeish(String oldTreeish) {
        this.oldTreeish = oldTreeish;
    }

    /**
     * Mutator for all attributes bool
     * 
     * @param all - true to show all attributes not just changed ones
     */
    public void setAll(boolean all) {
        this.all = all;
    }

    /**
     * Helper function to parse the given commit id's feature information
     * 
     * @param id - the id to parse out
     * @param geogig - an instance of geogig to run commands with
     * @return (Optional)NodeRef - the NodeRef that contains the metadata id and id needed to get
     *         the feature and featuretype
     * 
     * @throws CommandSpecException - if the treeid couldn't be resolved
     */
    private Optional<NodeRef> parseID(ObjectId id, Context geogig) {
        RevTree tree = geogig.command(RevObjectParse.class).setObjectId(id).call(RevTree.class)
                .get();

        return geogig.command(FindTreeChild.class).setParent(tree).setChildPath(path).call();
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
        if (path.trim().isEmpty()) {
            throw new CommandSpecException("Invalid path was specified");
        }

        final Context geogig = this.getRepositoryContext(context);
        ObjectId newId = geogig.command(ResolveTreeish.class).setTreeish(newTreeish).call().get();

        ObjectId oldId = geogig.command(ResolveTreeish.class).setTreeish(oldTreeish).call().get();

        RevFeature newFeature = null;
        RevFeatureType newFeatureType = null;

        RevFeature oldFeature = null;
        RevFeatureType oldFeatureType = null;

        Optional<NodeRef> ref = parseID(newId, geogig);

        if (ref.isPresent()) {
            newFeatureType = geogig.command(RevObjectParse.class)
                    .setObjectId(ref.get().getMetadataId()).call(RevFeatureType.class).get();
            newFeature = geogig.command(RevObjectParse.class).setObjectId(ref.get().getObjectId())
                    .call(RevFeature.class).get();
        }

        ref = parseID(oldId, geogig);

        if (ref.isPresent()) {
            oldFeatureType = geogig.command(RevObjectParse.class)
                    .setObjectId(ref.get().getMetadataId()).call(RevFeatureType.class).get();
            oldFeature = geogig.command(RevObjectParse.class).setObjectId(ref.get().getObjectId())
                    .call(RevFeature.class).get();
        }

        org.locationtech.geogig.plumbing.diff.FeatureDiff diff = new org.locationtech.geogig.plumbing.diff.FeatureDiff(
                path, newFeature, oldFeature, newFeatureType, oldFeatureType, all);

        context.setResponseContent(new CommandResponse() {

            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeFeatureDiffResponse(diff.getDiffs());
                out.finish();
            }
        });
    }
}
