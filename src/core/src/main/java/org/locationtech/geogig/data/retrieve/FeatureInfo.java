/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */

package org.locationtech.geogig.data.retrieve;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.repository.NodeRef;

/**
 * This is a simple class that correlates a NodeRef (which has a
 * path/feature-name/feature-type-metadata-id) to the corresponding RevFeature
 * (which has the actual serialized data - attributes).
 */
public class FeatureInfo {

    private NodeRef node;

    private RevFeature feature;

    public FeatureInfo(NodeRef node, RevFeature feature) {
        this.node = node;
        this.feature = feature;
    }

    public FeatureInfo(NodeRef node) {
        this.node = node;
    }

    public String getName() {
        return node.name();
    }

    public ObjectId getMetaDataID() {
        return node.getMetadataId();
    }

    public RevFeature getFeature() {
        return feature;
    }

    public void setFeature(RevFeature feature) {
        this.feature = feature;
    }

    public String getPath() {
        return node.path();
    }

}
