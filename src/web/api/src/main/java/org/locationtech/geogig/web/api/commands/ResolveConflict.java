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

import static com.google.common.base.Preconditions.checkState;

import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.UpdateTree;
import org.locationtech.geogig.porcelain.AddOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Web interface to resolve a single feature conflict
 */

public class ResolveConflict extends AbstractWebAPICommand {

    String path;

    String objectId;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        setPath(options.getRequiredValue("path"));
        setFeatureObjectId(options.getRequiredValue("objectid"));
    }

    /**
     * Mutator for the path variable
     * 
     * @param path - the path to the feature you want to add
     */
    public void setPath(String path) {
        this.path = path;
    }

    public void setFeatureObjectId(String objectId) {
        this.objectId = objectId;
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

        RevTree revTree = geogig.workingTree().getTree();

        ObjectId featureObjectId = ObjectId.valueOf(objectId);

        Optional<NodeRef> nodeRef = geogig.command(FindTreeChild.class).setParent(revTree)
                .setChildPath(NodeRef.parentPath(path)).call();
        Preconditions.checkArgument(nodeRef.isPresent(), "Invalid reference: %s",
                NodeRef.parentPath(path));

        RevFeatureType revFeatureType = geogig.command(RevObjectParse.class)
                .setObjectId(nodeRef.get().getMetadataId()).call(RevFeatureType.class).get();

        Optional<RevFeature> object = geogig.command(RevObjectParse.class)
                .setObjectId(featureObjectId).call(RevFeature.class);

        if (!object.isPresent()) {
            throw new CommandSpecException("Object ID could not be resolved to a feature.");
        }

        RevFeature revFeature = object.get();

        CoordinateReferenceSystem crs = revFeatureType.type().getCoordinateReferenceSystem();
        Envelope bounds = ReferencedEnvelope.create(crs);

        Optional<Object> o;
        for (int i = 0; i < revFeature.size(); i++) {
            o = revFeature.get(i);
            if (o.isPresent() && o.get() instanceof Geometry) {
                Geometry g = (Geometry) o.get();
                if (bounds.isNull()) {
                    bounds.init(JTS.bounds(g, crs));
                } else {
                    bounds.expandToInclude(JTS.bounds(g, crs));
                }
            }
        }

        NodeRef newFeatureNode = new NodeRef(
                RevObjectFactory.defaultInstance().createNode(NodeRef.nodeFromPath(path),
                        featureObjectId, ObjectId.NULL, TYPE.FEATURE, bounds, null),
                NodeRef.parentPath(path), ObjectId.NULL);

        Optional<NodeRef> parentNode = geogig.command(FindTreeChild.class)
                .setParent(geogig.workingTree().getTree())
                .setChildPath(newFeatureNode.getParentPath()).call();
        CanonicalTreeBuilder treeBuilder;
        ObjectId metadataId = ObjectId.NULL;
        if (parentNode.isPresent()) {
            metadataId = parentNode.get().getMetadataId();
            Optional<RevTree> parsed = geogig.command(RevObjectParse.class)
                    .setObjectId(parentNode.get().getNode().getObjectId()).call(RevTree.class);
            checkState(parsed.isPresent(), "Parent tree couldn't be found in the repository.");
            treeBuilder = CanonicalTreeBuilder.create(geogig.objectDatabase(), parsed.get());
            treeBuilder.remove(newFeatureNode.getNode());
        } else {
            treeBuilder = CanonicalTreeBuilder.create(geogig.objectDatabase());
        }
        treeBuilder.put(newFeatureNode.getNode());

        RevTree newFeatureTree = treeBuilder.build();
        NodeRef newTreeRef = NodeRef.tree(newFeatureNode.getParentPath(), newFeatureTree.getId(),
                metadataId);

        RevTree newRoot = geogig.command(UpdateTree.class).setRoot(geogig.workingTree().getTree())
                .setChild(newTreeRef).call();
        geogig.workingTree().updateWorkHead(newRoot.getId());

        AddOp command = geogig.command(AddOp.class);

        command.addPattern(path);

        command.call();

        context.setResponseContent(new CommandResponse() {
            @Override
            public void write(ResponseWriter out) throws Exception {
                out.start();
                out.writeElement("Add", "Success");
                out.finish();
            }
        });
    }
}
