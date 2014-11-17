/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.api.commands;

import static com.google.common.base.Preconditions.checkState;

import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.WriteBack;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.CommandResponse;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

/**
 * The interface for the Add operation in GeoGig.
 * 
 * Web interface for {@link AddOp}
 */

public class ResolveConflict extends AbstractWebAPICommand {

    private String path;

    private ObjectId objectId;

    /**
     * Mutator for the path variable
     * 
     * @param path - the path to the feature you want to add
     */
    public void setPath(String path) {
        this.path = path;
    }

    public void setFeatureObjectId(String objectId) {
        this.objectId = ObjectId.valueOf(objectId);
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
        if (this.getTransactionId() == null) {
            throw new CommandSpecException(
                    "No transaction was specified, add requires a transaction to preserve the stability of the repository.");
        }
        final Context geogig = this.getCommandLocator(context);

        RevTree revTree = geogig.workingTree().getTree();

        Optional<NodeRef> nodeRef = geogig.command(FindTreeChild.class).setParent(revTree)
                .setChildPath(NodeRef.parentPath(path)).setIndex(true).call();
        Preconditions.checkArgument(nodeRef.isPresent(), "Invalid reference: %s",
                NodeRef.parentPath(path));

        RevFeatureType revFeatureType = geogig.command(RevObjectParse.class)
                .setObjectId(nodeRef.get().getMetadataId()).call(RevFeatureType.class).get();

        RevFeature revFeature = geogig.command(RevObjectParse.class).setObjectId(objectId)
                .call(RevFeature.class).get();

        CoordinateReferenceSystem crs = revFeatureType.type().getCoordinateReferenceSystem();
        Envelope bounds = ReferencedEnvelope.create(crs);

        Optional<Object> o;
        for (int i = 0; i < revFeature.getValues().size(); i++) {
            o = revFeature.getValues().get(i);
            if (o.isPresent() && o.get() instanceof Geometry) {
                Geometry g = (Geometry) o.get();
                if (bounds.isNull()) {
                    bounds.init(JTS.bounds(g, crs));
                } else {
                    bounds.expandToInclude(JTS.bounds(g, crs));
                }
            }
        }

        NodeRef node = new NodeRef(Node.create(NodeRef.nodeFromPath(path), objectId, ObjectId.NULL,
                TYPE.FEATURE, bounds), NodeRef.parentPath(path), ObjectId.NULL);

        Optional<NodeRef> parentNode = geogig.command(FindTreeChild.class)
                .setParent(geogig.workingTree().getTree()).setChildPath(node.getParentPath())
                .setIndex(true).call();
        RevTreeBuilder treeBuilder = null;
        ObjectId metadataId = ObjectId.NULL;
        if (parentNode.isPresent()) {
            metadataId = parentNode.get().getMetadataId();
            Optional<RevTree> parsed = geogig.command(RevObjectParse.class)
                    .setObjectId(parentNode.get().getNode().getObjectId()).call(RevTree.class);
            checkState(parsed.isPresent(), "Parent tree couldn't be found in the repository.");
            treeBuilder = new RevTreeBuilder(geogig.objectDatabase(), parsed.get());
            treeBuilder.remove(node.getNode().getName());
        } else {
            treeBuilder = new RevTreeBuilder(geogig.stagingDatabase());
        }
        treeBuilder.put(node.getNode());
        ObjectId newTreeId = geogig
                .command(WriteBack.class)
                .setAncestor(
                        geogig.workingTree().getTree().builder(geogig.stagingDatabase()))
                .setChildPath(node.getParentPath()).setToIndex(true).setTree(treeBuilder.build())
                .setMetadataId(metadataId).call();
        geogig.workingTree().updateWorkHead(newTreeId);

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
