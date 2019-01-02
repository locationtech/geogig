/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.index;

import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.AbstractConsumer;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.repository.ProgressListener;

class SimpleTreeBuilderConsumer extends AbstractConsumer implements Consumer {

    protected final AtomicLong count = new AtomicLong();

    protected final RevTreeBuilder builder;

    protected final ProgressListener progress;

    SimpleTreeBuilderConsumer(RevTreeBuilder builder, ProgressListener listener) {
        this.builder = builder;
        this.progress = listener;
    }

    @Override
    public boolean tree(@Nullable NodeRef left, @Nullable NodeRef right) {
        return !progress.isCanceled();
    }

    @Override
    public boolean bucket(NodeRef leftParent, NodeRef rightParent, BucketIndex bucketIndex,
            @Nullable Bucket left, @Nullable Bucket right) {
        return !progress.isCanceled();
    }

    @Override
    public boolean feature(final @Nullable NodeRef left, @Nullable final NodeRef right) {
        final boolean cancelled = progress.isCanceled();
        if (!cancelled) {
            if (left == null) {
                Node node = right.getNode();
                boolean put = builder.put(node);
                checkState(put, "Node was not added to index: %s", node);
            } else if (right == null) {
                Node node = left.getNode();
                boolean removed = builder.remove(node);
                checkState(removed, "Node was not removed from index: %s", node);
            } else {
                Node lnode = left.getNode();
                Node rnode = right.getNode();
                boolean updated = builder.update(lnode, rnode);
                if (!lnode.equals(rnode)) {
                    checkState(updated, "Node %s was not updated to %s", lnode, rnode);
                }
            }
            progress.setProgress(count.incrementAndGet());
        }
        return !cancelled;
    }

}