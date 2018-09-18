/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.test.integration.remoting;

import static org.locationtech.geogig.test.integration.remoting.RemotesIndexTestSupport.createIndexes;
import static org.locationtech.geogig.test.integration.remoting.RemotesIndexTestSupport.verifyClonedIndexes;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp.CommandListener;
import org.locationtech.geogig.repository.Repository;

/**
 * {@link CloneOp} integration test suite for full clones (for shallow and sparse clones see
 * {@link ShallowCloneTest} and {@link SparseCloneTest})
 *
 */
public class CloneOpWithIndexTest extends CloneOpTest {

    /**
     * Override to set setCloneIndexes(true) and add a command hook that creates the indexes on the
     * remote repo before cloning it
     */
    protected @Override CloneOp cloneOp() {
        CloneOp cloneOp = super.cloneOp().setCloneIndexes(true);
        cloneOp.addListener(createSpatialIndexBeforeCloneListener);
        return cloneOp;
    }

    private CommandListener createSpatialIndexBeforeCloneListener = new CommandListener() {

        public @Override void preCall(AbstractGeoGigOp<?> command) {
            Repository remote = CloneOpWithIndexTest.this.remoteRepo;
            createIndexes(remote);
        }

        public @Override void postCall(AbstractGeoGigOp<?> command, @Nullable Object result,
                @Nullable RuntimeException exception) {

            Repository remote = CloneOpWithIndexTest.this.remoteRepo;
            Repository local = command.context().repository();
            CloneOp op = (CloneOp) command;
            if (result != null) {
                verifyClonedIndexes(local, remote, op.getBranch());
            }
        }
    };
}
