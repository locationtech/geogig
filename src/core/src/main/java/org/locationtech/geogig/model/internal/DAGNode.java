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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.datastream.FormatCommonV2_2;
import org.locationtech.geogig.storage.datastream.Varint;

abstract class DAGNode {

    public abstract Node resolve(TreeCache cache);

    public static DAGNode of(Node node) {
        return new DirectDAGNode(node);
    }

    public abstract boolean isNull();

    private static final byte MAGIC_DIRECT = 7;

    private static final byte MAGIC_LAZY_FEATURE = 9;

    private static final byte MAGIC_LAZY_TREE = 11;

    public static void encode(DAGNode node, DataOutput output) throws IOException {

        if (node instanceof DirectDAGNode) {
            output.writeByte(MAGIC_DIRECT);
            FormatCommonV2_2.INSTANCE.writeNode(((DirectDAGNode) node).node, output);
        } else {
            LazyDAGNode ln = (LazyDAGNode) node;
            if (ln instanceof TreeDAGNode) {
                output.writeByte(MAGIC_LAZY_TREE);
            } else {
                output.writeByte(MAGIC_LAZY_FEATURE);
            }
            final int leafRevTreeId = ln.leafRevTreeId;
            final int nodeIndex = ln.nodeIndex;
            Varint.writeUnsignedVarInt(leafRevTreeId, output);
            Varint.writeUnsignedVarInt(nodeIndex, output);
        }

    }

    public static DAGNode decode(DataInput in) throws IOException {
        final byte magic = in.readByte();
        switch (magic) {
        case MAGIC_DIRECT: {
            Node node = FormatCommonV2_2.INSTANCE.readNode(in);
            return DAGNode.of(node);
        }
        case MAGIC_LAZY_TREE: {
            int treeCacheId = Varint.readUnsignedVarInt(in);
            int nodeIndex = Varint.readUnsignedVarInt(in);
            DAGNode node = DAGNode.treeNode(treeCacheId, nodeIndex);
            return node;
        }
        case MAGIC_LAZY_FEATURE: {
            int treeCacheId = Varint.readUnsignedVarInt(in);
            int nodeIndex = Varint.readUnsignedVarInt(in);
            DAGNode node = DAGNode.featureNode(treeCacheId, nodeIndex);
            return node;
        }
        }
        throw new IllegalArgumentException("Invalid magic number, expected 7 or 9, got " + magic);
    }

    @Override
    public abstract boolean equals(Object o);

    static class DirectDAGNode extends DAGNode {

        private final Node node;

        public DirectDAGNode(Node node) {
            this.node = node;
        }

        @Override
        public Node resolve(TreeCache cache) {
            return node;
        }

        @Override
        public boolean isNull() {
            return node.getObjectId().isNull();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof DirectDAGNode)) {
                return false;
            }
            return node.equals(((DirectDAGNode) o).node);
        }
    }

    abstract static class LazyDAGNode extends DAGNode {

        protected final int leafRevTreeId;

        protected final int nodeIndex;

        public LazyDAGNode(final int leafRevTreeId, final int nodeIndex) {
            this.leafRevTreeId = leafRevTreeId;
            this.nodeIndex = nodeIndex;
        }

        @Override
        public final Node resolve(TreeCache cache) {
            RevTree tree = cache.resolve(leafRevTreeId);
            return resolve(tree);
        }

        protected abstract Node resolve(RevTree tree);

        @Override
        public boolean isNull() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LazyDAGNode)) {
                return false;
            }
            LazyDAGNode l = (LazyDAGNode) o;
            return leafRevTreeId == l.leafRevTreeId && nodeIndex == l.nodeIndex;
        }

        @Override
        public String toString() {
            return new StringBuilder(getClass().getSimpleName()).append("[").append(leafRevTreeId)
                    .append("/").append(nodeIndex).append("]").toString();
        }
    }

    static final class TreeDAGNode extends LazyDAGNode {

        TreeDAGNode(int leafRevTreeId, int nodeIndex) {
            super(leafRevTreeId, nodeIndex);
        }

        @Override
        protected Node resolve(RevTree tree) {
            return tree.getTree(this.nodeIndex);
        }

    }

    static final class FeatureDAGNode extends LazyDAGNode {

        FeatureDAGNode(int leafRevTreeId, int nodeIndex) {
            super(leafRevTreeId, nodeIndex);
        }

        @Override
        protected Node resolve(RevTree tree) {
            return tree.getFeature(this.nodeIndex);
        }
    }

    public static DAGNode treeNode(final int cacheTreeId, final int nodeIndex) {
        return new TreeDAGNode(cacheTreeId, nodeIndex);
    }

    public static DAGNode featureNode(final int cacheTreeId, final int nodeIndex) {
        return new FeatureDAGNode(cacheTreeId, nodeIndex);
    }
}