/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.Remote;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.ForEachRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.porcelain.SynchronizationException.StatusCode;
import org.locationtech.geogig.remote.IRemoteRepo;
import org.locationtech.geogig.remote.RemoteUtils;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.DeduplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;

/**
 * Update remote refs along with associated objects.
 * 
 * <b>NOTE:</b> so far we don't have the ability to merge non conflicting changes. Instead, the diff
 * list we get acts on whole objects, , so its possible that this operation overrites non
 * conflicting changes when pushing a branch that has non conflicting changes at both sides. This
 * needs to be revisited once we get more merge tools.
 */
public class PushOp extends AbstractGeoGigOp<Boolean> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PushOp.class);

    private boolean all;

    private List<String> refSpecs = new ArrayList<String>();

    private Supplier<Optional<Remote>> remoteSupplier;

    /**
     * @param all if {@code true}, push all refs under refs/heads/
     * @return {@code this}
     */
    public PushOp setAll(final boolean all) {
        this.all = all;
        return this;
    }

    /**
     * @param refSpec the refspec of a remote branch
     * @return {@code this}
     */
    public PushOp addRefSpec(final String refSpec) {
        refSpecs.add(refSpec);
        return this;
    }

    public List<String> getRefSpecs() {
        return refSpecs;
    }

    /**
     * @param remoteName the name or URL of a remote repository to push to
     * @return {@code this}
     */
    public PushOp setRemote(final String remoteName) {
        checkNotNull(remoteName);
        return setRemote(command(RemoteResolve.class).setName(remoteName));
    }

    public String getRemoteName() {
        if (remoteSupplier == null) {
            return null;
        }
        String name = null;
        Optional<Remote> remote = this.remoteSupplier.get();
        if (remote.isPresent()) {
            name = remote.get().getName();
        }
        return name;
    }

    /**
     * @param remoteSupplier a supplier for the remote repository to push to
     * @return {@code this}
     */
    public PushOp setRemote(Supplier<Optional<Remote>> remoteSupplier) {
        checkNotNull(remoteSupplier);
        this.remoteSupplier = remoteSupplier;

        return this;
    }

    public Optional<Remote> getRemote() {
        return remoteSupplier.get();
    }

    /**
     * Executes the push operation.
     * 
     * @return {@code null}
     * @see org.locationtech.geogig.api.AbstractGeoGigOp#call()
     */
    @Override
    protected Boolean _call() {
        if (remoteSupplier == null) {
            setRemote("origin");
        }

        final Remote remote = resolveRemote();
        final IRemoteRepo remoteRepo = openRemoteRepo(remote);
        boolean dataPushed = false;
        try {
            dataPushed = callInternal(remote, remoteRepo, dataPushed);
        } finally {
            try {
                remoteRepo.close();
            } catch (IOException e) {
                Throwables.propagate(e);
            }
        }

        return dataPushed;
    }

    private boolean callInternal(final Remote remote, final IRemoteRepo remoteRepo,
            boolean dataPushed) {

        if (!refSpecs.isEmpty()) {
            for (String refspec : refSpecs) {
                String[] refs = refspec.split(":");
                if (refs.length == 0) {
                    refs = new String[2];
                    refs[0] = "";
                    refs[1] = "";
                }
                checkArgument(refs.length < 3,
                        "Invalid refspec, please use [+][<localref>][:][<remoteref>].");

                // @todo: REVISIT! looks like 'force' is being ignored
                final boolean force = refspec.length() > 0 && refspec.charAt(0) == '+';
                final String localrefspec = refs[0].substring(force ? 1 : 0);
                final String remoterefspec = (refs.length == 2 ? refs[1] : localrefspec);

                if (localrefspec.isEmpty()) {
                    if (remoterefspec.isEmpty()) {
                        // push current branch
                        Ref currentBranch = resolveHeadTarget();
                        dataPushed = push(remoteRepo, remote, currentBranch, null);
                    } else {
                        // delete the remote branch matching remoteref
                        remoteRepo.deleteRef(remoterefspec);
                    }
                } else {
                    Optional<Ref> local = refParse(localrefspec);
                    checkArgument(local.isPresent(), "Local ref '%s' could not be resolved.",
                            localrefspec);
                    // push the localref branch to the remoteref branch
                    Ref localRef = local.get();
                    dataPushed = push(remoteRepo, remote, localRef, remoterefspec);
                }

            }
        } else {
            List<Ref> refsToPush = new ArrayList<Ref>();
            if (all) {
                ImmutableSet<Ref> localRefs = getLocalRefs();
                refsToPush.addAll(localRefs);
            } else {
                // push current branch
                Ref currentBranch = resolveHeadTarget();
                refsToPush.add(currentBranch);
            }

            for (Ref localRef : refsToPush) {
                String remoteRef = null;
                dataPushed = push(remoteRepo, remote, localRef, remoteRef);
            }
        }
        return dataPushed;
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

    private Remote resolveRemote() {
        final Remote remote;
        Optional<Remote> pushRemote = remoteSupplier.get();
        checkArgument(pushRemote.isPresent(), "Remote could not be resolved.");

        remote = pushRemote.get();
        return remote;
    }

    private boolean push(IRemoteRepo remoteRepo, Remote remote, Ref localRef,
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
                return false;
            }
            throw Throwables.propagate(e);
        }

        LOGGER.info("Pushing {} to {}({})", localRef, localRemoteRefName, remoteRefSpec);
        updateRef(localRef.getObjectId(), localRemoteRefName);
        return true;
    }

    private ImmutableSet<Ref> getLocalRefs() {
        Predicate<Ref> filter = new Predicate<Ref>() {
            final String prefix = Ref.HEADS_PREFIX;

            @Override
            public boolean apply(Ref input) {
                return input.getName().startsWith(prefix);
            }
        };
        ImmutableSet<Ref> localRefs = command(ForEachRef.class).setFilter(filter).call();
        return localRefs;
    }

    private Ref resolveHeadTarget() {
        final Optional<Ref> currHead = refParse(Ref.HEAD);
        checkState(currHead.isPresent(), "Repository has no HEAD, can't push.");
        checkState(currHead.get() instanceof SymRef, "Can't push from detached HEAD");

        final Optional<Ref> headTarget = refParse(((SymRef) currHead.get()).getTarget());
        checkState(headTarget.isPresent());
        return headTarget.get();
    }

    private void updateRef(ObjectId objectId, String refName) {
        this.command(UpdateRef.class).setNewValue(objectId).setName(refName).call();
    }

    private Optional<Ref> refParse(String refSpec) {
        return command(RefParse.class).setName(refSpec).call();
    }

    /**
     * @param remote the remote to get
     * @return an interface for the remote repository
     */
    public Optional<IRemoteRepo> getRemoteRepo(Remote remote) {
        Hints remoteHints = new Hints();
        remoteHints.set(Hints.REMOTES_READ_ONLY, Boolean.FALSE);
        Repository localRepository = repository();
        DeduplicationService deduplicationService = context.deduplicationService();
        return RemoteUtils.newRemote(GlobalContextBuilder.builder.build(remoteHints), remote,
                localRepository, deduplicationService);
    }
}
