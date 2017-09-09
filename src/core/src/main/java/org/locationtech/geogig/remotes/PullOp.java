/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remotes;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.NothingToCommitException;
import org.locationtech.geogig.porcelain.RebaseOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Remote;

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

    public boolean isAll() {
        return all;
    }

    /**
     * @param rebase if {@code true}, perform a rebase on the remote branch instead of a merge
     * @return {@code this}
     */
    public PullOp setRebase(final boolean rebase) {
        this.rebase = rebase;
        return this;
    }

    public boolean isRebase() {
        return rebase;
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

    public Integer getDepth() {
        return depth.orNull();
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

    public boolean isFullDepth() {
        return fullDepth;
    }

    /**
     * @param refSpec the refspec of a remote branch
     * @return {@code this}
     */
    public PullOp addRefSpec(final String refSpec) {
        refSpecs.add(refSpec);
        return this;
    }

    public List<String> getRefSpecs() {
        return refSpecs;
    }

    /**
     * @param remoteName the name or URL of a remote repository to fetch from
     * @return {@code this}
     */
    public PullOp setRemote(final String remoteName) {
        Preconditions.checkNotNull(remoteName);
        return setRemote(command(RemoteResolve.class).setName(remoteName));
    }

    public String getRemoteName() {
        if (remote == null) {
            return null;
        }
        String name = null;
        Optional<Remote> remote = this.remote.get();
        if (remote.isPresent()) {
            name = remote.get().getName();
        }
        return name;
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

    public String getAuthor() {
        return authorName.orNull();
    }

    public String getAuthorEmail() {
        return authorEmail.orNull();
    }

    /**
     * Executes the pull operation.
     * 
     * @return {@code null}
     * @see org.locationtech.geogig.repository.AbstractGeoGigOp#call()
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

        TransferSummary fetchResult = command(FetchOp.class).addRemote(remote).setDepth(depth.or(0))
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
            final Optional<Ref> sourceRefOpt = findRemoteRef(remoteref);
            if (!sourceRefOpt.isPresent()) {
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

            final Ref sourceRef = sourceRefOpt.get();
            final ObjectId sourceOid = sourceRef.getObjectId();
            final Optional<Ref> destRefOpt = command(RefParse.class).setName(destinationref).call();
            if (destRefOpt.isPresent()) {
                final Ref destRef = destRefOpt.get();
                if (destRef.getObjectId().equals(sourceOid) || sourceOid.isNull()) {
                    // Already up to date.
                    result.setOldRef(destRef);
                    result.setNewRef(destRef);
                    continue;
                }
                result.setOldRef(destRef);

                final boolean forceCheckout = destRef.getObjectId().isNull();
                command(CheckoutOp.class).setSource(destinationref).setForce(forceCheckout).call();
                if (rebase) {
                    command(RebaseOp.class).setUpstream(Suppliers.ofInstance(sourceOid)).call();
                } else {
                    try {
                        MergeReport report = command(MergeOp.class)
                                .setAuthor(authorName.orNull(), authorEmail.orNull())
                                .addCommit(sourceOid).call();
                        result.setMergeReport(Optional.of(report));
                    } catch (NothingToCommitException e) {
                        // the branch that we are trying to pull has less history than the
                        // branch we are pulling into
                    }
                }
                result.setNewRef(command(RefParse.class).setName(destinationref).call().get());
            } else {
                // make a new branch
                Ref newRef = command(BranchCreateOp.class).setAutoCheckout(true)
                        .setName(destinationref).setSource(sourceOid.toString()).call();
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
