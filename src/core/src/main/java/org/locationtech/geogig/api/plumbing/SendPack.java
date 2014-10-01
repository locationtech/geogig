package org.locationtech.geogig.api.plumbing;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.locationtech.geogig.api.porcelain.TransferSummary.ChangedRef.ChangeTypes.ADDED_REF;
import static org.locationtech.geogig.api.porcelain.TransferSummary.ChangedRef.ChangeTypes.CHANGED_REF;
import static org.locationtech.geogig.api.porcelain.TransferSummary.ChangedRef.ChangeTypes.REMOVED_REF;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.Remote;
import org.locationtech.geogig.api.hooks.Hookable;
import org.locationtech.geogig.api.porcelain.SynchronizationException;
import org.locationtech.geogig.api.porcelain.SynchronizationException.StatusCode;
import org.locationtech.geogig.api.porcelain.TransferSummary;
import org.locationtech.geogig.api.porcelain.TransferSummary.ChangedRef;
import org.locationtech.geogig.api.porcelain.TransferSummary.ChangedRef.ChangeTypes;
import org.locationtech.geogig.remote.IRemoteRepo;
import org.locationtech.geogig.remote.RemoteUtils;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.DeduplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
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
            checkArgument(delete || localRef != null, "localRef can only be null if delete == true");
            checkArgument(!delete || remoteRef != null, "remoteRef can't be null if delete == true");

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

        final IRemoteRepo remoteRepo = openRemoteRepo(remote);
        TransferSummary transferResult;
        try {
            transferResult = callInternal(remoteRepo);
            checkState(transferResult != null);
        } finally {
            try {
                remoteRepo.close();
            } catch (IOException e) {
                Throwables.propagate(e);
            }
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

        TransferSummary result = new TransferSummary();

        for (TransferableRef ref : this.refsToPush) {
            localRefSpec = ref.getLocalRef();
            remoteRefSpec = ref.getRemoteRef();
            force = ref.isForceUpdate();

            if (ref.isDelete()) {
                Optional<Ref> deleted = remoteRepo.deleteRef(remoteRefSpec);
                if (deleted.isPresent()) {
                    ChangedRef deleteResult = new ChangedRef(deleted.get(), null, REMOVED_REF);
                    result.add(remote.getPushURL(), deleteResult);
                }
            } else {
                Optional<Ref> localRef = refParse(localRefSpec);
                checkState(localRef.isPresent(), "RefSpec %s does not exist", localRefSpec);

                Optional<Ref> newRef = push(remoteRepo, remote, localRef.get(), remoteRefSpec);

                if (newRef.isPresent()) {
                    ChangeTypes changeType = remoteRefSpec == null ? ADDED_REF : CHANGED_REF;
                    ChangedRef deleteResult = new ChangedRef(localRef.get(), newRef.get(),
                            changeType);
                    result.add(remote.getPushURL(), deleteResult);
                }
            }
        }
        return result;
    }

    private IRemoteRepo openRemoteRepo(final Remote remote) {
        final IRemoteRepo remoteRepo;
        Optional<IRemoteRepo> resolvedRemoteRepo = getRemoteRepo(remote);
        checkState(resolvedRemoteRepo.isPresent(), "Failed to connect to the remote.");

        remoteRepo = resolvedRemoteRepo.get();
        try {
            remoteRepo.open();
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return remoteRepo;
    }

    private Optional<Ref> push(IRemoteRepo remoteRepo, Remote remote, Ref localRef,
            @Nullable String remoteRefSpec) {

        String localRemoteRefName;
        try {
            if (null == remoteRefSpec) {
                localRemoteRefName = Ref.append(Ref.REMOTES_PREFIX, remote.getName() + "/"
                        + localRef.localName());
                remoteRepo.pushNewData(localRef, getProgressListener());
            } else {
                localRemoteRefName = Ref.append(Ref.REMOTES_PREFIX, remote.getName() + "/"
                        + remoteRefSpec);
                remoteRepo.pushNewData(localRef, remoteRefSpec, getProgressListener());
            }
        } catch (SynchronizationException e) {
            if (e.statusCode == StatusCode.NOTHING_TO_PUSH) {
                return Optional.absent();
            }
            throw Throwables.propagate(e);
        }

        // update the local copy of the remote ref
        LOGGER.info("Pushing {} to {}({})", localRef, localRemoteRefName, remoteRefSpec);
        Optional<Ref> updateRef = updateRef(localRef.getObjectId(), localRemoteRefName);
        return updateRef;
    }

    private Optional<Ref> updateRef(ObjectId objectId, String refName) {
        return this.command(UpdateRef.class).setNewValue(objectId).setName(refName).call();
    }

    /**
     * @param remote the remote to get
     * @return an interface for the remote repository
     */
    @VisibleForTesting
    public Optional<IRemoteRepo> getRemoteRepo(Remote remote) {
        Hints remoteHints = new Hints();
        remoteHints.set(Hints.REMOTES_READ_ONLY, Boolean.FALSE);
        Repository localRepository = repository();
        DeduplicationService deduplicationService = context.deduplicationService();
        return RemoteUtils.newRemote(GlobalContextBuilder.builder.build(remoteHints), remote,
                localRepository, deduplicationService);
    }

    private Optional<Ref> refParse(String refSpec) {
        return command(RefParse.class).setName(refSpec).call();
    }

}
