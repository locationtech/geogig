/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - factored out from MergeOp
 */
package org.locationtech.geogig.plumbing.merge;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.ResolveBranchId;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.StagingArea;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.impl.PersistedIterable;
import org.locationtech.geogig.storage.impl.PersistedIterable.Serializer;

import com.google.common.base.Optional;

public class MergeStatusBuilder extends MergeScenarioConsumer {

    final PersistedIterable<FeatureInfo> mergedBuffer = ConflictsUtils
            .newTemporaryFeatureInfoStream();

    final PersistedIterable<Conflict> conflictsBuffer = ConflictsUtils.newTemporaryConflictStream();

    final PersistedIterable<DiffEntry> unconflictedBuffer = ConflictsUtils
            .newTemporaryDiffEntryStream();

    static final int maxReportedConflicts = 25;

    final AtomicInteger reportedConflicts = new AtomicInteger(0);

    private AtomicBoolean fastForward = new AtomicBoolean(true);

    private AtomicBoolean changed = new AtomicBoolean(false);

    private StringBuilder mergeMsg = new StringBuilder();

    // In case there are conflicts
    private StringBuilder conflictMsg = new StringBuilder();

    private final Context context;

    private final StagingArea index;

    private final WorkingTree workingTree;

    private final boolean ours;

    private final ProgressListener progress;

    public MergeStatusBuilder(Context context, boolean ours, List<ObjectId> commits,
            ProgressListener progress) {
        this.context = context;
        this.index = context.stagingArea();
        this.workingTree = context.workingTree();
        this.ours = ours;
        this.progress = progress;
        progress.setMaxProgress(0);

        ObjectId commitId = commits.get(0);
        Optional<Ref> ref = context.command(ResolveBranchId.class).setObjectId(commitId).call();
        if (ref.isPresent()) {
            mergeMsg.append("Merge branch " + ref.get().getName());
        } else {
            mergeMsg.append("Merge commit '" + commitId.toString() + "'. ");
        }
        mergeMsg.append("\n\nConflicts:\n");
    }

    public String getMergeMessage() {
        return mergeMsg.toString();
    }

    public String getConflictsMessage() {
        return conflictMsg.toString();
    }

    public void setChanged(boolean changed) {
        this.changed.set(changed);
    }

    public boolean isChanged() {
        return this.changed.get();
    }

    public void setFastFoward(boolean ff) {
        this.fastForward.set(ff);
    }

    public boolean isFastForward() {
        return this.fastForward.get();
    }

    @Override
    public void conflicted(Conflict conflict) {
        if (!ours) {
            conflictsBuffer.add(conflict);
        }
        if (reportedConflicts.get() < maxReportedConflicts) {
            mergeMsg.append("\t" + conflict.getPath() + "\n");
            conflictMsg.append("CONFLICT: Merge conflict in " + conflict.getPath() + "\n");
            reportedConflicts.incrementAndGet();
        }
        progress.setProgress(1f + progress.getProgress());
    }

    @Override
    public void unconflicted(DiffEntry diff) {
        unconflictedBuffer.add(diff);
        changed.set(true);
        fastForward.set(false);
        progress.setProgress(1f + progress.getProgress());
    }

    @Override
    public void merged(FeatureInfo featureInfo) {
        mergedBuffer.add(featureInfo);
        changed.set(true);
        fastForward.set(false);
        progress.setProgress(1f + progress.getProgress());
    }

    @Override
    protected void cancelled() {
        dispose();
    }

    @Override
    public void finished() {
        progress.complete();
        progress.started();
        try {
            AutoCloseableIterator<DiffEntry> unstaged = AutoCloseableIterator.emptyIterator();
            if (mergedBuffer.size() > 0) {
                progress.setDescription(
                        String.format("Saving %,d merged features...", mergedBuffer.size()));
                workingTree.insert(mergedBuffer.iterator(), progress);
                unstaged = workingTree.getUnstaged(null);
            }
            if (unconflictedBuffer.size() > 0 || unstaged.hasNext()) {
                progress.setDescription(
                        String.format("Staging %,d unconflicted and %,d merged differences...",
                                unconflictedBuffer.size(), mergedBuffer.size()));

                long size = unconflictedBuffer.size() + mergedBuffer.size();
                unstaged = AutoCloseableIterator.concat(unstaged,
                        AutoCloseableIterator.fromIterator(unconflictedBuffer.iterator()));
                // Stage it
                index.stage(progress, unstaged, size);
            }
            if (conflictsBuffer.size() > 0) {
                // Write the conflicts
                progress.setDescription(
                        String.format("Saving %,d conflicts...", conflictsBuffer.size()));
                context.command(ConflictsWriteOp.class).setConflicts(conflictsBuffer).call();
            }
        } finally {
            dispose();
        }

        if (reportedConflicts.get() > maxReportedConflicts) {
            mergeMsg.append("and " + (reportedConflicts.get() - maxReportedConflicts)
                    + " additional conflicts.\n");
            conflictMsg
                    .append("and " + (reportedConflicts.get() - maxReportedConflicts) + " more.\n");
        }
        conflictMsg.append("Automatic merge failed. Fix conflicts and then commit the result.\n");
    }

    private void dispose() {
        try {
            conflictsBuffer.close();
        } finally {
            try {
                unconflictedBuffer.close();
            } finally {
                mergedBuffer.close();
            }
        }
    }

    static Serializer<ObjectId> OID = new Serializer<ObjectId>() {

        @Override
        public void write(DataOutputStream out, ObjectId value) throws IOException {
            value.writeTo(out);
        }

        @Override
        public ObjectId read(DataInputStream in) throws IOException {
            return ObjectId.readFrom(in);
        }
    };
}
