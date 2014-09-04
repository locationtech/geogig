/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing;

import java.io.IOException;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.Remote;
import org.locationtech.geogig.remote.IRemoteRepo;
import org.locationtech.geogig.remote.RemoteUtils;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.DeduplicationService;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;

/**
 * Connects to the specified remote, retrieves its {@link Ref refs}, closes the remote connection
 * and returns the list of remote references.
 */
public class LsRemote extends AbstractGeoGigOp<ImmutableSet<Ref>> {

    private Supplier<Optional<Remote>> remote;

    private boolean getHeads;

    private boolean getTags;

    private boolean local;

    /**
     * Constructs a new {@code LsRemote}.
     */
    public LsRemote() {
        Optional<Remote> abstent = Optional.absent();
        this.remote = Suppliers.ofInstance(abstent);
        this.getHeads = true;
        this.getTags = true;
    }

    /**
     * @param remote the remote whose refs should be listed
     * @return {@code this}
     */
    public LsRemote setRemote(Supplier<Optional<Remote>> remote) {
        this.remote = remote;
        return this;
    }

    /**
     * @param getHeads tells whether to retrieve remote heads, defaults to {@code true}
     * @return {@code this}
     */
    public LsRemote retrieveHeads(boolean getHeads) {
        this.getHeads = getHeads;
        return this;
    }

    /**
     * @param getTags tells whether to retrieve remote tags, defaults to {@code true}
     * @return {@code this}
     */
    public LsRemote retrieveTags(boolean getTags) {
        this.getTags = getTags;
        return this;
    }

    /**
     * @param local if {@code true} retrieves the refs of the remote repository known to the local
     *        repository instead (i.e. those under the {@code refs/remotes/<remote name>} namespace
     *        in the local repo. Defaults to {@code false}
     * @return {@code this}
     */
    public LsRemote retrieveLocalRefs(boolean local) {
        this.local = local;
        return this;
    }

    /**
     * Lists all refs for the given remote.
     * 
     * @return an immutable set of the refs for the given remote
     */
    @Override
    protected  ImmutableSet<Ref> _call() {
        Preconditions.checkState(remote.get().isPresent(), "Remote was not provided");
        final Remote remoteConfig = remote.get().get();

        if (local) {
            return locallyKnownRefs(remoteConfig);
        }
        getProgressListener().setDescription("Obtaining remote " + remoteConfig.getName());
        Optional<IRemoteRepo> remoteRepo = getRemoteRepo(remoteConfig);
        Preconditions.checkState(remoteRepo.isPresent(), "Remote could not be opened.");
        getProgressListener().setDescription("Connecting to remote " + remoteConfig.getName());
        try {
            remoteRepo.get().open();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        getProgressListener().setDescription(
                "Connected to remote " + remoteConfig.getName() + ". Retrieving references");
        ImmutableSet<Ref> remoteRefs;
        try {
            remoteRefs = remoteRepo.get().listRefs(getHeads, getTags);
        } finally {
            try {
                remoteRepo.get().close();
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
        return remoteRefs;
    }

    /**
     * @param remote the remote to get
     * @return an interface for the remote repository
     */
    public Optional<IRemoteRepo> getRemoteRepo(Remote remote) {
        Repository localRepository = repository();
        DeduplicationService deduplicationService = context.deduplicationService();
        return RemoteUtils.newRemote(GlobalContextBuilder.builder.build(Hints.readOnly()), remote,
                localRepository, deduplicationService);
    }

    /**
     * @see ForEachRef
     */
    private ImmutableSet<Ref> locallyKnownRefs(final Remote remoteConfig) {
        Predicate<Ref> filter = new Predicate<Ref>() {
            final String prefix = Ref.REMOTES_PREFIX + remoteConfig.getName() + "/";

            @Override
            public boolean apply(Ref input) {
                return input.getName().startsWith(prefix);
            }
        };
        return command(ForEachRef.class).setFilter(filter).call();
    }

}
