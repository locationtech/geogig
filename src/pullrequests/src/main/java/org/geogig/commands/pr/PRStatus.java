/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.geogig.commands.pr;

import java.util.List;
import java.util.Optional;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Wither;

public @Value @Builder @Wither class PRStatus {

    private @NonNull PR request;

    private @NonNull Optional<ObjectId> mergeCommit;

    private @NonNull Optional<MergeScenarioReport> report;

    // a merged pr is both closed and merged. If it was not applied it's just closed
    private boolean closed, merged;

    private long numConflicts;

    private int commitsBehindTargetBranch;

    private int commitsBehindRemoteBranch;

    private @NonNull List<String> affectedLayers;

    public boolean isTargetBranchBehind() {
        return getCommitsBehindTargetBranch() > 0;
    }

    public boolean isRemoteBranchBehind() {
        return getCommitsBehindRemoteBranch() > 0;
    }

    public boolean isConflicted() {
        return getNumConflicts() > 0L;
    }
}
