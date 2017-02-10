/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Resolves the feature type associated to a refspec.
 * 
 * If the refspecs resolves to a tree, it returns the default feature type of the tree. If it
 * resolves to a feature, it returns its feature type.
 * 
 * @see RevParse
 * @see FindTreeChild
 */
public class ResolveFeatureType extends AbstractGeoGigOp<Optional<RevFeatureType>> {

    private String refSpec;

    /**
     * @param treeIshRefSpec a ref spec that resolves to the tree or feature node holding the
     *        {@link Node#getMetadataId() metadataId} of the {@link RevFeatureType} to parse It can
     *        be a full refspec or just a path. In this last case, the path is assumed to refer to
     *        the working tree, and "WORK_HEAD:" is appended to the path to create the full refspec
     * @return
     */
    public ResolveFeatureType setRefSpec(String refSpec) {
        this.refSpec = refSpec;
        return this;
    }

    @Override
    protected Optional<RevFeatureType> _call() {
        Preconditions.checkState(refSpec != null, "ref spec has not been set.");
        final String fullRefspec;
        if (refSpec.contains(":")) {
            fullRefspec = refSpec;
        } else {
            fullRefspec = Ref.WORK_HEAD + ":" + refSpec;
        }
        final String ref = fullRefspec.substring(0, fullRefspec.indexOf(':'));
        final String path = fullRefspec.substring(fullRefspec.indexOf(':') + 1);

        ObjectId parentId = command(ResolveTreeish.class).setTreeish(ref).call().get();
        Optional<RevTree> parent = command(RevObjectParse.class).setObjectId(parentId)
                .call(RevTree.class);
        if (!parent.isPresent()) {
            return Optional.absent();
        }
        Optional<NodeRef> node = command(FindTreeChild.class).setParent(parent.get())
                .setChildPath(path).call();
        if (!node.isPresent()) {
            return Optional.absent();
        }
        NodeRef found = node.get();
        ObjectId metadataID = found.getMetadataId();
        Optional<RevFeatureType> ft = command(RevObjectParse.class).setObjectId(metadataID)
                .call(RevFeatureType.class);
        return ft;
    }
}