/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Returns the NodeRef corresponding to a given refspec, if available.
 */
// The annotation is here to allow this being run from the 'conflicts' command.
// Other than that, there is no reason for this to be restricted to non-conflicting scenarios
@CanRunDuringConflict
public class FeatureNodeRefFromRefspec extends AbstractGeoGigOp<Optional<NodeRef>> {

    private String ref;

    public FeatureNodeRefFromRefspec setRefspec(String ref) {
        this.ref = ref;
        return this;
    }

    private RevFeatureType getFeatureTypeFromRefSpec() {

        String featureTypeRef = NodeRef.parentPath(ref);
        String fullRef;
        if (featureTypeRef.contains(":")) {
            fullRef = featureTypeRef;
        } else {
            fullRef = "WORK_HEAD:" + featureTypeRef;
        }

        String treeRef = fullRef.split(":")[0];
        String path = fullRef.split(":")[1];
        ObjectId revTreeId = command(ResolveTreeish.class).setTreeish(treeRef).call().get();
        RevTree revTree = command(RevObjectParse.class).setObjectId(revTreeId).call(RevTree.class)
                .get();

        Optional<NodeRef> nodeRef = command(FindTreeChild.class).setParent(revTree)
                .setChildPath(path).call();
        Preconditions.checkArgument(nodeRef.isPresent(), "Invalid reference: %s", ref);

        RevFeatureType revFeatureType = command(RevObjectParse.class)
                .setObjectId(nodeRef.get().getMetadataId()).call(RevFeatureType.class).get();
        return revFeatureType;

    }

    private Optional<RevFeature> getFeatureFromRefSpec() {

        Optional<RevObject> revObject = command(RevObjectParse.class).setRefSpec(ref)
                .call(RevObject.class);

        if (!revObject.isPresent()) { // let's try to see if it is a feature in the working tree
            NodeRef.checkValidPath(ref);
            Optional<NodeRef> elementRef = command(FindTreeChild.class)
                    .setParent(workingTree().getTree()).setChildPath(ref).call();
            Preconditions.checkArgument(elementRef.isPresent(), "Invalid reference: %s", ref);
            ObjectId id = elementRef.get().getObjectId();
            revObject = command(RevObjectParse.class).setObjectId(id).call(RevObject.class);
        }

        if (revObject.isPresent()) {
            Preconditions.checkArgument(TYPE.FEATURE.equals(revObject.get().getType()),
                    "%s does not resolve to a feature", ref);
            return Optional.of(RevFeature.class.cast(revObject.get()));
        } else {
            return Optional.absent();
        }
    }

    @Override
    protected Optional<NodeRef> _call() {

        Optional<RevFeature> feature = getFeatureFromRefSpec();

        if (feature.isPresent()) {
            RevFeatureType featureType = getFeatureTypeFromRefSpec();
            RevFeature feat = feature.get();
            Envelope bounds = SpatialOps.boundsOf(feat);
            Node node = RevObjectFactory.defaultInstance().createNode(NodeRef.nodeFromPath(ref),
                    feat.getId(), featureType.getId(), TYPE.FEATURE, bounds, null);
            return Optional.of(new NodeRef(node, NodeRef.parentPath(ref), featureType.getId()));

        }
        return Optional.absent();
    }
}
