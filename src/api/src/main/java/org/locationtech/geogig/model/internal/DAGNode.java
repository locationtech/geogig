/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.ObjectStore;

import lombok.Getter;
import lombok.experimental.Accessors;

public abstract class DAGNode {

    public abstract Node resolve(ObjectStore store);

    public static DAGNode of(Node node) {
        return new DirectDAGNode(node);
    }

    public abstract boolean isNull();

    public @Override abstract boolean equals(Object o);

    public static class DirectDAGNode extends DAGNode {

        private final @Getter Node node;

        public DirectDAGNode(Node node) {
            this.node = node;
        }

        public @Override Node resolve(ObjectStore store) {
            return node;
        }

        public @Override boolean isNull() {
            return node.getObjectId().isNull();
        }

        public @Override boolean equals(Object o) {
            if (!(o instanceof DirectDAGNode)) {
                return false;
            }
            return node.equals(((DirectDAGNode) o).node);
        }
    }

    public abstract static @Accessors(fluent = true) class LazyDAGNode extends DAGNode {

        protected final @Getter ObjectId leafRevTreeId;

        protected final @Getter int nodeIndex;

        public LazyDAGNode(final ObjectId leafRevTreeId, final int nodeIndex) {
            this.leafRevTreeId = leafRevTreeId;
            this.nodeIndex = nodeIndex;
        }

        public @Override final Node resolve(ObjectStore store) {
            RevTree tree = store.getTree(leafRevTreeId);
            return resolve(tree);
        }

        protected abstract Node resolve(RevTree tree);

        public @Override boolean isNull() {
            return false;
        }

        public @Override boolean equals(Object o) {
            if (!(o instanceof LazyDAGNode)) {
                return false;
            }
            LazyDAGNode l = (LazyDAGNode) o;
            return leafRevTreeId.equals(l.leafRevTreeId) && nodeIndex == l.nodeIndex;
        }

        public @Override String toString() {
            return new StringBuilder(getClass().getSimpleName()).append("[").append(leafRevTreeId)
                    .append("/").append(nodeIndex).append("]").toString();
        }
    }

    public static final class TreeDAGNode extends LazyDAGNode {

        TreeDAGNode(ObjectId leafRevTreeId, int nodeIndex) {
            super(leafRevTreeId, nodeIndex);
        }

        protected @Override Node resolve(RevTree tree) {
            return tree.getTree(this.nodeIndex);
        }

    }

    public static final class FeatureDAGNode extends LazyDAGNode {

        FeatureDAGNode(ObjectId leafRevTreeId, int nodeIndex) {
            super(leafRevTreeId, nodeIndex);
        }

        protected @Override Node resolve(RevTree tree) {
            return tree.getFeature(this.nodeIndex);
        }
    }

    public static DAGNode treeNode(final ObjectId cacheTreeId, final int nodeIndex) {
        return new TreeDAGNode(cacheTreeId, nodeIndex);
    }

    public static DAGNode featureNode(final ObjectId cacheTreeId, final int nodeIndex) {
        return new FeatureDAGNode(cacheTreeId, nodeIndex);
    }
}