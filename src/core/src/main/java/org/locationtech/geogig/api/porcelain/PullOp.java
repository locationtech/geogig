/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.porcelain;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.Remote;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.porcelain.MergeOp.MergeReport;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Incorporates changes from a remote repository into the current branch.
 * 
 */
public class PullOp extends AbstractGeoGigOp<PullResult> {

    private boolean all;

    private boolean rebase;

    private boolean fullDepth = false;

    private Supplier<Optional<Remote>> remote;

    private List<String> refSpecs = new ArrayList<String>();

    private Optional<Integer> depth = Optional.absent();

    private Optional<String> authorName = Optional.absent();

    private Optional<String> authorEmail = Optional.absent();

    /**
     * @param all if {@code true}, pull from all remotes.
     * @return {@code this}
     */
    public PullOp setAll(final boolean all) {
        this.all = all;
        return this;
    }

    /**
     * @param rebase if {@code true}, perform a rebase on the remote branch instead of a merge
     * @return {@code this}
     */
    public PullOp setRebase(final boolean rebase) {
        this.rebase = rebase;
        return this;
    }

    /**
     * If no depth is specified, fetch will pull all history from the specified ref(s). If the
     * repository is shallow, it will maintain the existing depth.
     * 
     * @param depth maximum commit depth to pull
     * @return {@code this}
     */
    public PullOp setDepth(final int depth) {
        if (depth > 0) {
            this.depth = Optional.of(depth);
        }
        return this;
    }

    /**
     * If full depth is set on a shallow clone, then the full history will be pulled.
     * 
     * @param fulldepth whether or not to pull the full history
     * @return {@code this}
     */
    public PullOp setFullDepth(boolean fullDepth) {
        this.fullDepth = fullDepth;
        return this;
    }

    /**
     * @param refSpec the refspec of a remote branch
     * @return {@code this}
     */
    public PullOp addRefSpec(final String refSpec) {
        refSpecs.add(refSpec);
        return this;
    }

    /**
     * @param remoteName the name or URL of a remote repository to fetch from
     * @return {@code this}
     */
    public PullOp setRemote(final String remoteName) {
        Preconditions.checkNotNull(remoteName);
        return setRemote(command(RemoteResolve.class).setName(remoteName));
    }

    /**
     * @param remoteSupplier the remote repository to fetch from
     * @return {@code this}
     */
    public PullOp setRemote(Supplier<Optional<Remote>> remoteSupplier) {
        Preconditions.checkNotNull(remoteSupplier);
        remote = remoteSupplier;

        return this;
    }

    /**
     * 
     * @param author the author of the commit
     * @param email email of author
     * @return {@code this}
     */
    public PullOp setAuthor(@Nullable String authorName, @Nullable String authorEmail) {
        this.authorName = Optional.fromNullable(authorName);
        this.authorEmail = Optional.fromNullable(authorEmail);
        return this;
    }

    /**
     * Executes the pull operation.
     * 
     * @return {@code null}
     * @see org.locationtech.geogig.api.AbstractGeoGigOp#call()
     */
    @Override
    protected PullResult _call() {

        if (remote == null) {
            setRemote("origin");
        }

        PullResult result = new PullResult();

        Optional<Remote> remoteRepo = remote.get();

        Preconditions.checkArgument(remoteRepo.isPresent(), "Remote could not be resolved.");
        getProgressListener().started();

        FetchResult fetchResult = command(FetchOp.class).addRemote(remote).setDepth(depth.or(0))
                .setFullDepth(fullDepth).setAll(all).setProgressListener(subProgress(80.f)).call();

        result.setFetchResult(fetchResult);

        if (refSpecs.size() == 0) {
            // pull current branch
            final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
            Preconditions.checkState(currHead.isPresent(), "Repository has no HEAD, can't pull.");
            Preconditions.checkState(currHead.get() instanceof SymRef,
                    "Can't pull from detached HEAD");
            final SymRef headRef = (SymRef) currHead.get();
            final String currentBranch = Ref.localName(headRef.getTarget());

            refSpecs.add(currentBranch + ":" + currentBranch);
        }

        for (String refspec : refSpecs) {
            String[] refs = refspec.split(":");
            Preconditions.checkArgument(refs.length < 3,
                    "Invalid refspec, please use [+]<remoteref>[:<localref>].");

            boolean force = refspec.length() > 0 && refspec.charAt(0) == '+';
            String remoteref = refs[0].substring(force ? 1 : 0);
            Optional<Ref> sourceRef = findRemoteRef(remoteref);
            if (!sourceRef.isPresent()) {
                continue;
            }

            String destinationref = "";
            if (refs.length == 2) {
                destinationref = refs[1];
            } else {
                // pull into current branch
                final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
                Preconditions.checkState(currHead.isPresent(),
                        "Repository has no HEAD, can't pull.");
                Preconditions.checkState(currHead.get() instanceof SymRef,
                        "Can't pull from detached HEAD");
                final SymRef headRef = (SymRef) currHead.get();
                destinationref = headRef.getTarget();
            }

            Optional<Ref> destRef = command(RefParse.class).setName(destinationref).call();
            if (destRef.isPresent()) {
                if (destRef.get().getObjectId().equals(sourceRef.get().getObjectId())
                        || sourceRef.get().getObjectId().equals(ObjectId.NULL)) {
                    // Already up to date.
                    result.setOldRef(destRef.get());
                    result.setNewRef(destRef.get());
                    continue;
                }
                result.setOldRef(destRef.get());
                if (destRef.get().getObjectId().equals(ObjectId.NULL)) {
                    command(UpdateRef.class).setName(destRef.get().getName())
                            .setNewValue(sourceRef.get().getObjectId()).call();
                } else {
                    command(CheckoutOp.class).setSource(destinationref).call();
                    if (rebase) {
                        command(RebaseOp.class).setUpstream(
                                Suppliers.ofInstance(sourceRef.get().getObjectId())).call();
                    } else {
                        try {
                            MergeReport report = command(MergeOp.class)
                                    .setAuthor(authorName.orNull(), authorEmail.orNull())
                                    .addCommit(Suppliers.ofInstance(sourceRef.get().getObjectId()))
                                    .call();
                            result.setMergeReport(Optional.of(report));
                        } catch (NothingToCommitException e) {
                            // the branch that we are trying to pull has less history than the
                            // branch we are pulling into
                        }
                    }
                }
                destRef = command(RefParse.class).setName(destinationref).call();
                result.setNewRef(destRef.get());
            } else {
                // make a new branch
                Ref newRef = command(BranchCreateOp.class).setAutoCheckout(true)
                        .setName(destinationref)
                        .setSource(sourceRef.get().getObjectId().toString()).call();
                result.setNewRef(newRef);
            }

        }

        getProgressListener().complete();

        result.setRemoteName(remote.get().get().getFetchURL());

        return result;
    }

    /**
     * @param ref the ref to find
     * @return an {@link Optional} of the ref, or {@link Optional#absent()} if it wasn't found
     */
    public Optional<Ref> findRemoteRef(String ref) {

        String remoteRef = Ref.REMOTES_PREFIX + remote.get().get().getName() + "/" + ref;
        return command(RefParse.class).setName(remoteRef).call();
    }
}
