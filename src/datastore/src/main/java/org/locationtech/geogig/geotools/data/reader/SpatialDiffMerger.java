/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorates the {@link PreOrderDiffWalk} {@link Consumer} when performing a diff between quad trees
 * to merge delete and add feature events against the same feature as a single modification event.
 * <p>
 * Given quad trees are spatially clustered instead of following the canonical order, it could be
 * that a feature modification is reported twice if the change made it fall into a different index
 * quadrant.
 * <p>
 * This consumer wrapper merges such events into one modification event.
 * <p>
 * NOTE the order the add, delete, and merged modification events are reported is altered and hence
 * this is not to be used whenever the diff events are expected to follow proper in-order traversal.
 * It is useful though for the feature source to produce a stream of features with accurate
 * reporting of feature changes.
 */
public class SpatialDiffMerger extends PreOrderDiffWalk.ForwardingConsumer {

    private static final Logger log = LoggerFactory.getLogger(SpatialDiffMerger.class);

    private long maxHeldFeatures, totalFeatureEvents, totalMerged;

    private static class FeatureEvent {
        private NodeRef left, right;

        FeatureEvent(NodeRef left, NodeRef right) {
            this.left = left;
            this.right = right;
        }

        public String name() {
            return SpatialDiffMerger.name(left, right);
        }

        //@formatter:off
        public void setLeft(NodeRef l) {if (l != null) {left = l;}}
        public void setRight(NodeRef r) {if (r != null) {right = r;}}
        //@formatter:on

        public boolean merge(FeatureEvent e) {
            if ((left != null && left.equals(e.right)) || (right != null && right.equals(e.left))) {
                return false;
            }
            setLeft(e.left);
            setRight(e.right);
            return true;
        }
    }

    public SpatialDiffMerger() {
        super();
    }

    public SpatialDiffMerger(Consumer delegate) {
        super(delegate);
    }

    private Map<String, FeatureEvent> featureEvents = new LinkedHashMap<>();

    private boolean flush() {
        final int size = featureEvents.size();
        if (size == 0) {
            return true;
        }
        int adds = 0, removes = 0;
        for (Iterator<FeatureEvent> it = featureEvents.values().iterator(); it.hasNext();) {//@formatter:off
            FeatureEvent fe = it.next();
            it.remove();
            NodeRef left = fe.left;
            NodeRef right = fe.right;
            if(left == null) adds++;
            if(right == null) removes++;//@formatter:on
            if (!super.feature(left, right)) {
                return false;
            }
        }

        if (log.isInfoEnabled()) {
            log.info(String.format(
                    "Flushed %,d of %,d feature events: +%,d -%,d ~%,d, max held features: %,d",
                    size, totalFeatureEvents, adds, removes, totalMerged, maxHeldFeatures));
        }
        return true;
    }

    private boolean treePassThru;

    public @Override boolean tree(NodeRef left, NodeRef right) {
        boolean passthru = RevTree.EMPTY_TREE_ID.equals(left.getObjectId())
                || RevTree.EMPTY_TREE_ID.equals(right.getObjectId());

        treePassThru = passthru;

        return super.tree(left, right);
    }

    public @Override void endTree(NodeRef left, NodeRef right) {
        flush();
        super.endTree(left, right);
    }

    public @Override boolean bucket(NodeRef leftParent, NodeRef rightParent,
            BucketIndex bucketIndex, @Nullable Bucket left, @Nullable Bucket right) {

        if (treePassThru || (left != null && right != null)) {
            return super.bucket(leftParent, rightParent, bucketIndex, left, right);
        }
        return true;
    }

    public @Override void endBucket(NodeRef leftParent, NodeRef rightParent,
            BucketIndex bucketIndex, @Nullable Bucket left, @Nullable Bucket right) {

        if (treePassThru || (left != null && right != null)) {
            super.endBucket(leftParent, rightParent, bucketIndex, left, right);
        }
    }

    public @Override boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
        ++totalFeatureEvents;
        if (treePassThru) {
            return super.feature(left, right);
        }
        if (left != null && right != null) {
            return super.feature(left, right);
        }

        FeatureEvent featureEvent = new FeatureEvent(left, right);
        FeatureEvent previous = featureEvents.putIfAbsent(featureEvent.name(), featureEvent);
        maxHeldFeatures = Math.max(maxHeldFeatures, featureEvents.size());
        if (previous != null) {
            featureEvents.remove(previous.name());
            if (previous.merge(featureEvent)) {
                ++totalMerged;
                return super.feature(previous.left, previous.right);
            } // else ignore, they're the same, represent no change
        }
        final int flushThreshold = 10_000_000;
        if (featureEvents.size() == flushThreshold) {
            treePassThru = true;
            return flush();
        }
        return true;
    }

    private static String name(NodeRef left, NodeRef right) {
        return left == null ? right.name() : left.name();
    }
}