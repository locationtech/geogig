/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.flatbuffers;

import java.util.Collections;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.flatbuffers.generated.v1.LeafTree;
import org.locationtech.geogig.flatbuffers.generated.v1.SHA;
import org.locationtech.geogig.flatbuffers.generated.v1.values.Bounds;
import org.locationtech.geogig.flatbuffers.generated.v1.values.Dictionary;
import org.locationtech.geogig.flatbuffers.generated.v1.values.MapEntry;
import org.locationtech.geogig.flatbuffers.generated.v1.values.Value;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

abstract @RequiredArgsConstructor class FBNode extends org.locationtech.geogig.model.Node {

    private final @NonNull LeafTree tree;

    private final int nodeIndex;

    private static class TreeNode extends FBNode {
        public TreeNode(LeafTree tree, int nodeIndex) {
            super(tree, nodeIndex);
        }

        public @Override TYPE getType() {
            return TYPE.TREE;
        }
    }

    private static class FeatureNode extends FBNode {
        public FeatureNode(LeafTree tree, int nodeIndex) {
            super(tree, nodeIndex);
        }

        public @Override TYPE getType() {
            return TYPE.FEATURE;
        }
    }

    public static FBNode treeNode(LeafTree tree, int nodeIndex) {
        return new TreeNode(tree, nodeIndex);
    }

    public static FBNode featureNode(LeafTree tree, int nodeIndex) {
        return new FeatureNode(tree, nodeIndex);
    }

    private Bounds boundsInternal() {
        return tree.nodesBounds(nodeIndex);
    }

    public @Override boolean intersects(Envelope env) {
        return FBAdapters.intersects(boundsInternal(), env);
    }

    public @Override void expand(Envelope env) {
        FBAdapters.expandEnv(env, boundsInternal());
    }

    public @Override Optional<Envelope> bounds() {
        return FBAdapters.toEnvelopeOpt(boundsInternal());
    }

    public @Override String getName() {
        return tree.nodesNames(nodeIndex);
    }

    public @Override ObjectId getObjectId() {
        return FBAdapters.toId(tree.nodesIds(nodeIndex));
    }

    public @Override Optional<ObjectId> getMetadataId() {
        final SHA mdid = tree.nodesMetadataIds(nodeIndex);
        ObjectId id = ObjectId.NULL;
        if (mdid != null) {
            id = FBAdapters.toId(mdid);
        }
        if (id.isNull()) {
            return Optional.absent();
        }
        return Optional.of(id);
    }

    public @Override Map<String, Object> getExtraData() {
        if (0 == tree.nodesExtraDataLength()) {
            return Collections.emptyMap();
        }
        Dictionary nodeDict = tree.nodesExtraData(nodeIndex);
        if (nodeDict == null) {
            return Collections.emptyMap();
        }
        return ValueSerializer.decode(nodeDict);
    }

    public @Nullable @Override Object getExtraData(@NonNull String key) {
        Dictionary nodeDict = tree.nodesExtraData(nodeIndex);
        if (nodeDict == null) {
            return null;
        }
        MapEntry entry = nodeDict.entriesByKey(key);
        if (entry == null) {
            return null;
        }
        Value value = entry.value();
        return ValueSerializer.decodeValue(value);
    }

}
