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

import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.impl.RevTreeBuilder;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.AbstractConsumer;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.Consumer;
import org.locationtech.geogig.repository.NodeRef;
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
                builder.put(right.getNode());
            } else if (right == null) {
                builder.remove(left.getNode());
            } else {
                builder.update(left.getNode(), right.getNode());
            }
            progress.setProgress(count.incrementAndGet());
        }
        return !cancelled;
    }

}