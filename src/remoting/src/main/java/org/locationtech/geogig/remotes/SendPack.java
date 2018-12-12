/* Copyright (c) 2014-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.remotes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.remotes.SynchronizationException.StatusCode;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

@Hookable(name = "send-pack")
public class SendPack extends AbstractGeoGigOp<TransferSummary> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendPack.class);

    public static class TransferableRef {

        private final String localRef;

        private final String remoteRef;

        private final boolean forceUpdate;

        private boolean delete;

        public TransferableRef(final @Nullable String localRef, final @Nullable String remoteRef,
                final boolean forceUpdate, final boolean delete) {
            checkArgument(delete || localRef != null,
                    "localRef can only be null if delete == true");
            checkArgument(!delete || remoteRef != null,
                    "remoteRef can't be null if delete == true");

            this.localRef = localRef;
            this.remoteRef = remoteRef;
            this.forceUpdate = forceUpdate;
            this.delete = delete;
        }

        public String getLocalRef() {
            return localRef;
        }

        @Nullable
        public String getRemoteRef() {
            return remoteRef;
        }

        public boolean isForceUpdate() {
            return forceUpdate;
        }

        public boolean isDelete() {
            return delete;
        }

    }

    private List<TransferableRef> refsToPush = new ArrayList<>();

    private Remote remote;

    public SendPack addRef(TransferableRef refToPush) {
        checkNotNull(refToPush);
        this.refsToPush.add(refToPush);
        return this;
    }

    public SendPack setRefs(List<TransferableRef> refsToPush) {
        checkNotNull(refsToPush);
        for (TransferableRef tr : refsToPush) {
            checkNotNull(tr);
        }
        this.refsToPush.clear();
        this.refsToPush.addAll(refsToPush);
        return this;
    }

    public ImmutableList<TransferableRef> getRefs() {
        return ImmutableList.copyOf(refsToPush);
    }

    public SendPack setRemote(Remote remote) {
        checkNotNull(remote);
        this.remote = remote;
        return this;
    }

    public Remote getRemote() {
        return remote;
    }

    @Override
    protected TransferSummary _call() {
        checkState(remote != null, "no remote specified");
        checkState(!refsToPush.isEmpty(), "no refs to push specified");

        TransferSummary transferResult;
        try (IRemoteRepo remoteRepo = openRemote(remote)) {
            transferResult = callInternal(remoteRepo);
            checkState(transferResult != null);
        }

        return transferResult;
    }

    private TransferSummary callInternal(IRemoteRepo remoteRepo) {

        final Remote remote = this.remote;
        @Nullable
        String localRefSpec;
        @Nullable
        String remoteRefSpec;
        boolean force;

        TransferSummary sendPackResult = new TransferSummary();

        for (TransferableRef ref : this.refsToPush) {
            localRefSpec = ref.getLocalRef();
            remoteRefSpec = ref.getRemoteRef();
            force = ref.isForceUpdate();

            if (ref.isDelete()) {
                Optional<Ref> deleted = remoteRepo.deleteRef(remoteRefSpec);
                if (deleted.isPresent()) {
                    RefDiff deleteResult = RefDiff.added(deleted.get());
                    sendPackResult.add(remote.getPushURL(), deleteResult);
                }
            } else {
                Optional<Ref> localRef = refParse(localRefSpec);
                checkState(localRef.isPresent(), "RefSpec %s does not exist", localRefSpec);

                Optional<Ref> newRef = push(remoteRepo, remote, localRef.get(), remoteRefSpec);

                if (newRef.isPresent()) {
                    RefDiff deleteResult = new RefDiff(localRef.get(), newRef.get());
                    sendPackResult.add(remote.getPushURL(), deleteResult);
                }
            }
        }
        return sendPackResult;
    }

    private Optional<Ref> push(IRemoteRepo remoteRepo, Remote remote, Ref localRef,
            @Nullable String remoteRefSpec) {

        String localRemoteRefName;
        try {
            Repository localRepo = repository();
            if (null == remoteRefSpec) {
                localRemoteRefName = Ref.append(Ref.REMOTES_PREFIX,
                        remote.getName() + "/" + localRef.localName());
                remoteRepo.pushNewData(localRepo, localRef, getProgressListener());
            } else {
                localRemoteRefName = Ref.append(Ref.REMOTES_PREFIX,
                        remote.getName() + "/" + remoteRefSpec);
                remoteRepo.pushNewData(localRepo, localRef, remoteRefSpec, getProgressListener());
            }
        } catch (SynchronizationException e) {
            if (e.statusCode == StatusCode.NOTHING_TO_PUSH) {
                return Optional.absent();
            }
            throw e;
        }

        // update the local copy of the remote ref
        LOGGER.info("Pushing {} to {}({})", localRef, localRemoteRefName, remoteRefSpec);
        Optional<Ref> updateRef = updateRef(localRef.getObjectId(), localRemoteRefName);
        return updateRef;
    }

    private Optional<Ref> updateRef(ObjectId objectId, String refName) {
        return this.command(UpdateRef.class).setNewValue(objectId).setName(refName).call();
    }

    private IRemoteRepo openRemote(Remote remote) {
        return command(OpenRemote.class).setRemote(remote).call();
    }

    private Optional<Ref> refParse(String refSpec) {
        return command(RefParse.class).setName(refSpec).call();
    }

}
