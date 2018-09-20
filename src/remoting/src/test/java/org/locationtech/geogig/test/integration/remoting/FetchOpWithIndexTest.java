/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.test.integration.remoting;

import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.test.integration.remoting.RemotesIndexTestSupport.createIndexes;
import static org.locationtech.geogig.test.integration.remoting.RemotesIndexTestSupport.verifyClonedIndexes;

import java.util.Collection;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.remotes.CloneOp;
import org.locationtech.geogig.remotes.FetchOp;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.TransferSummary;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp.CommandListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;

import com.google.common.base.Optional;

public class FetchOpWithIndexTest extends FetchOpTest {

    /**
     * Override to set setCloneIndexes(true) and add a command hook that creates the indexes on the
     * remote repo before cloning it
     */
    protected @Override CloneOp cloneOp() {
        CloneOp cloneOp = super.cloneOp().setCloneIndexes(true);
        cloneOp.addListener(createSpatialIndexBeforeCloneListener);
        return cloneOp;
    }

    protected @Override FetchOp fetchOp() throws RepositoryConnectionException {
        FetchOp fetchOp = super.fetchOp();
        fetchOp.addListener(verifyFetchedIndexesListener);
        return fetchOp.setFetchIndexes(true);
    }

    private CommandListener verifyFetchedIndexesListener = new CommandListener() {

        public @Override void preCall(AbstractGeoGigOp<?> command) {// nothing to do
            createIndexes(FetchOpWithIndexTest.this.originRepo);
            createIndexes(FetchOpWithIndexTest.this.upstreamRepo);
        }

        public @Override void postCall(AbstractGeoGigOp<?> command, @Nullable Object result,
                @Nullable RuntimeException exception) {
            if (exception != null) {
                return;
            }
            TransferSummary ts = (TransferSummary) result;
            Set<String> remotes = ts.getRefDiffs().keySet();
            for (String remoteURI : remotes) {
                FetchOp op = (FetchOp) command;
                java.util.Optional<Remote> remoteObj = op.getRemotes().stream()
                        .filter(r -> remoteURI.equals(r.getFetchURL())).findFirst();
                assertTrue(remoteObj.isPresent());

                Repository remote;
                String originURI = originRepo.getLocation().toString();
                if (originURI.startsWith(remoteURI)) {
                    remote = originRepo;
                } else {
                    remote = upstreamRepo;
                }
                Collection<RefDiff> collection = ts.getRefDiffs().get(remoteURI);
                for (RefDiff rd : collection) {
                    if (rd.isDelete()) {
                        continue;
                    }
                    Ref newRef = rd.getNewRef();
                    Repository local = command.context().repository();
                    String localRef = newRef.getName();
                    String remoteBranch = remoteObj.get().mapToRemote(localRef).orElse(null);
                    verifyClonedIndexes(local, remote, Optional.of(localRef),
                            Optional.of(remoteBranch));
                }
            }
        }
    };

    private CommandListener createSpatialIndexBeforeCloneListener = new CommandListener() {

        public @Override void preCall(AbstractGeoGigOp<?> command) {
            Repository remote = FetchOpWithIndexTest.this.originRepo;
            createIndexes(remote);
        }

        public @Override void postCall(AbstractGeoGigOp<?> command, @Nullable Object result,
                @Nullable RuntimeException exception) {
        }
    };
}
