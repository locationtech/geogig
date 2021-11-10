/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.ForEachRef;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;

import com.google.common.base.Preconditions;

/**
 * Connects to the specified remote, retrieves its {@link Ref refs}, closes the remote connection
 * and returns the list of remote references.
 */
public class LsRemoteOp extends AbstractGeoGigOp<Set<Ref>> {

    // optional, if not supplied #remoteRepo is mandatory
    private Supplier<Optional<Remote>> remote;

    // optional, if not supplied #remote is mandatory, if supplied #local must be false
    private IRemoteRepo remoteRepo;

    private boolean getHead = false;

    private boolean getBranches = true;

    private boolean getTags = true;

    private boolean local = false;

    /**
     * Constructs a new {@code LsRemote}.
     */
    public LsRemoteOp() {
        this.remote = () -> Optional.empty();
        this.getBranches = true;
        this.getTags = true;
    }

    /**
     * @param remote the remote whose refs should be listed
     * @return {@code this}
     */
    public LsRemoteOp setRemote(Supplier<Optional<Remote>> remote) {
        this.remote = remote;
        this.remoteRepo = null;
        return this;
    }

    public LsRemoteOp setRemote(Remote remote) {
        this.remote = () -> Optional.of(remote);
        this.remoteRepo = null;
        return this;
    }

    public LsRemoteOp setRemote(IRemoteRepo remoteRepo) {
        this.remoteRepo = remoteRepo;
        this.remote = () -> Optional.of(remoteRepo.getInfo());
        return this;
    }

    /**
     * Find the remote to be listed
     */
    public Optional<Remote> getRemote() {
        return remote.get();
    }

    /**
     * @param getHeads tells whether to retrieve remote heads (i.e. branches), defaults to
     *        {@code true}
     * @return {@code this}
     */
    public LsRemoteOp retrieveBranches(boolean getHeads) {
        this.getBranches = getHeads;
        return this;
    }

    /**
     * @param getTags tells whether to retrieve remote tags, defaults to {@code true}
     * @return {@code this}
     */
    public LsRemoteOp retrieveTags(boolean getTags) {
        this.getTags = getTags;
        return this;
    }

    /**
     * @param local if {@code true} retrieves the refs of the remote repository known to the local
     *        repository instead (i.e. those under the {@code refs/remotes/<remote name>} namespace
     *        in the local repo. Defaults to {@code false}
     * @return {@code this}
     */
    public LsRemoteOp retrieveLocalRefs(boolean local) {
        this.local = local;
        return this;
    }

    /**
     * Whether to retrieve the remote's {@link Ref#HEAD HEAD} ref, deaults to {@code false}
     */
    public LsRemoteOp retrieveHead(boolean getHead) {
        this.getHead = getHead;
        return this;
    }

    /**
     * Lists all refs for the given remote.
     * 
     * @return an immutable set of the refs for the given remote
     */
    protected @Override Set<Ref> _call() {
        final Remote remoteConfig = this.remote.get().orElse(null);

        Preconditions.checkState(remoteRepo != null || remoteConfig != null,
                "Remote was not provided");

        if (local) {
            checkArgument(remoteConfig != null,
                    "if retrieving local remote refs, a Remote must be provided");
            return locallyKnownRefs(remoteConfig);
        }

        Set<Ref> remoteRefs;
        IRemoteRepo remoteRepo = this.remoteRepo;
        final boolean closeRemote = remoteRepo == null;
        if (remoteRepo == null) {
            remoteRepo = openRemote(remoteConfig);
            getProgressListener().setDescription(
                    "Connected to remote " + remoteConfig.getName() + ". Retrieving references");
        }

        Optional<Ref> headRef = Optional.empty();
        try {
            remoteRefs = remoteRepo.listRefs(repository(), getBranches, getTags);
            if (getHead) {
                headRef = remoteRepo.headRef();
            }
        } finally {
            if (closeRemote) {
                remoteRepo.close();
            }
        }

        if (headRef.isPresent()) {
            Set<Ref> refs = new TreeSet<>(remoteRefs);
            refs.add(headRef.get());
            remoteRefs = refs;
        }

        Set<Ref> filtered = remoteRefs.stream()
                .filter(r -> remoteConfig.mapToLocal(r.getName()).isPresent())
                .collect(Collectors.toCollection(TreeSet::new));

        return filtered;

    }

    private IRemoteRepo openRemote(Remote remote) {
        return command(OpenRemote.class).setRemote(remote).readOnly().call();
    }

    /**
     * @see ForEachRef
     */
    private Set<Ref> locallyKnownRefs(final Remote remoteConfig) {

        Predicate<Ref> filter = input -> remoteConfig.mapToRemote(input.getName()).isPresent();

        return command(ForEachRef.class).setFilter(filter).call();
    }

}
