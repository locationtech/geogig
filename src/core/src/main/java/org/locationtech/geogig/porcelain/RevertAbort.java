/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Given one or more existing commits, revert the changes that the related patches introduce, and
 * record some new commits that record them. This requires your working tree to be clean (no
 * modifications from the HEAD commit).
 * 
 */
@CanRunDuringConflict
public class RevertAbort extends AbstractGeoGigOp<Void> {

    private static final Logger log = LoggerFactory.getLogger(RevertAbort.class);

    protected @Override Void _call() {

        getProgressListener().started();

        Optional<Ref> origHead = command(RefParse.class).setName(Ref.ORIG_HEAD).call();
        Preconditions.checkState(origHead.isPresent(),
                "Cannot abort. You are not in the middle of a revert process.");

        // Revert can only be run in a conflicted situation if the abort option is used
        final boolean hasConflicts = conflictsDatabase().hasConflicts(null);
        if (hasConflicts) {
            log.debug("Aborting revert op, will reset conflicts too");
        }

        final ObjectId commitId = origHead.get().getObjectId();
        command(ResetOp.class).setMode(ResetMode.HARD).setCommit(commitId).call();
        command(UpdateRef.class).setDelete(true).setName(Ref.ORIG_HEAD).call();

        getProgressListener().complete();
        return null;
    }
}
