/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - factored out from MergeOp
 */
package org.locationtech.geogig.api.porcelain;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.FeatureInfo;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.plumbing.ResolveBranchId;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.api.plumbing.merge.ConflictsWriteOp;
import org.locationtech.geogig.api.plumbing.merge.MergeScenarioConsumer;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

public class MergeStatusBuilder extends MergeScenarioConsumer {

    private final static int BUFFER_SIZE = 1000;

    final List<Conflict> conflictsBuffer = Lists.newArrayListWithCapacity(BUFFER_SIZE);

    final List<DiffEntry> diffEntryBuffer = Lists.newArrayListWithCapacity(BUFFER_SIZE);

    static final int maxReportedConflicts = 25;

    final AtomicInteger reportedConflicts = new AtomicInteger(0);

    private AtomicBoolean fastForward = new AtomicBoolean(true);

    private AtomicBoolean changed = new AtomicBoolean(false);

    private StringBuilder mergeMsg = new StringBuilder();

    // In case there are conflicts
    private StringBuilder conflictMsg = new StringBuilder();

    private final Context context;

    private final boolean ours;

    private final ProgressListener progress;

    public MergeStatusBuilder(Context context, boolean ours, List<ObjectId> commits,
            ProgressListener progress) {
        this.context = context;
        this.ours = ours;
        this.progress = progress;

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
            if (conflictsBuffer.size() == BUFFER_SIZE) {
                // Write the conflicts
                context.command(ConflictsWriteOp.class).setConflicts(conflictsBuffer).call();
                conflictsBuffer.clear();
            }
        }
        if (reportedConflicts.get() < maxReportedConflicts) {
            mergeMsg.append("\t" + conflict.getPath() + "\n");
            conflictMsg.append("CONFLICT: Merge conflict in " + conflict.getPath() + "\n");
            reportedConflicts.incrementAndGet();
        }
    }

    @Override
    public void unconflicted(DiffEntry diff) {
        diffEntryBuffer.add(diff);
        if (diffEntryBuffer.size() == BUFFER_SIZE) {
            // Stage it
            context.index().stage(progress, diffEntryBuffer.iterator(), 0);
            diffEntryBuffer.clear();
        }
        changed.set(true);
        fastForward.set(false);
    }

    @Override
    public void merged(FeatureInfo featureInfo) {
        context.workingTree().insert(NodeRef.parentPath(featureInfo.getPath()),
                featureInfo.getFeature());
        Iterator<DiffEntry> unstaged = context.workingTree().getUnstaged(null);
        context.index().stage(progress, unstaged, 0);
        changed.set(true);
        fastForward.set(false);
    }

    @Override
    public void finished() {
        if (conflictsBuffer.size() > 0) {
            // Write the conflicts
            context.command(ConflictsWriteOp.class).setConflicts(conflictsBuffer).call();
            conflictsBuffer.clear();
        }
        if (diffEntryBuffer.size() > 0) {
            // Stage it
            context.index().stage(progress, diffEntryBuffer.iterator(), 0);
            diffEntryBuffer.clear();
        }

        if (reportedConflicts.get() > maxReportedConflicts) {
            mergeMsg.append("and " + (reportedConflicts.get() - maxReportedConflicts)
                    + " additional conflicts.\n");
            conflictMsg
                    .append("and " + (reportedConflicts.get() - maxReportedConflicts) + " more.\n");
        }
        conflictMsg.append("Automatic merge failed. Fix conflicts and then commit the result.\n");
    }
}
