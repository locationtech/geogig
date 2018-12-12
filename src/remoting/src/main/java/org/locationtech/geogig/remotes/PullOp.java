/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remotes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.remotes.RemoteResolve;
import org.locationtech.geogig.porcelain.BranchResolveOp;
import org.locationtech.geogig.porcelain.MergeOp;
import org.locationtech.geogig.porcelain.MergeOp.MergeReport;
import org.locationtech.geogig.porcelain.NothingToCommitException;
import org.locationtech.geogig.porcelain.RebaseOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.LocalRemoteRefSpec;
import org.locationtech.geogig.repository.Remote;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Incorporates changes from a remote repository into the current branch.
 * 
 */
public class PullOp extends AbstractGeoGigOp<PullResult> {

    private boolean all;

    private boolean rebase;

    private boolean noFastForward;

    private boolean fullDepth = false;

    private boolean includeIndexes = false;

    private Supplier<Optional<Remote>> remote;

    private List<String> refSpecs = new ArrayList<String>();

    private Optional<Integer> depth = Optional.absent();

    private Optional<String> authorName = Optional.absent();

    private Optional<String> authorEmail = Optional.absent();

    private String message;

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

    public PullOp setNoFastForward(boolean noFF) {
        this.noFastForward = noFF;
        return this;
    }

    public PullOp setMessage(String message) {
        this.message = message;
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
     * @param refSpec specifies a remote ref to fetch and optionally which local ref to update,
     *        using the format {@code <remote ref>[:<localRef>]}
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
        checkNotNull(remoteName);
        return setRemote(command(RemoteResolve.class).setName(remoteName));
    }

    public PullOp setRemote(final Remote remote) {
        checkNotNull(remote);
        return setRemote(Suppliers.ofInstance(Optional.of(remote)));
    }

    /**
     * @param remoteSupplier the remote repository to fetch from
     * @return {@code this}
     */
    public PullOp setRemote(Supplier<Optional<Remote>> remoteSupplier) {
        checkNotNull(remoteSupplier);
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
     * If {@code true}, also synchronize the spatial indexes. Defaults to {@code false}.
     */
    public PullOp setIncludeIndexes(boolean pullIndexes) {
        this.includeIndexes = pullIndexes;
        return this;
    }

    public boolean isIncludeIndexes() {
        return includeIndexes;
    }

    public String getAuthor() {
        return authorName.orNull();
    }

    public String getAuthorEmail() {
        return authorEmail.orNull();
    }

    /**
     * Fetches the remote refs (and their contents) indicated by the {@link #addRefSpec(String)
     * ref-specs} and merges them onto the current branch.
     * 
     * @return {@code null}
     * @see org.locationtech.geogig.repository.AbstractGeoGigOp#call()
     */
    @Override
    protected PullResult _call() {
        if (remote == null) {
            setRemote("origin");
        }
        final Remote suppliedRemote = this.remote.get().orNull();
        checkArgument(suppliedRemote != null, "Remote could not be resolved.");

        final Ref currentBranch = resolveCurrentBranch();

        if (refSpecs.isEmpty()) {
            // TODO: use the Remote to resolve the local current branch to the remote branch
            // mapping?
            final String currentBranchName = currentBranch.localName();
            refSpecs.add(String.format("%s:%s", currentBranchName, currentBranchName));
        }

        final Remote remote;// remote with the appropriate refspecs to fetch and where to
        {
            String localRemoteRefSpec = Joiner.on(';').join(refSpecs);
            remote = suppliedRemote.fetch(localRemoteRefSpec);
        }

        getProgressListener().started();
        getProgressListener().setDescription("Pull: pulling " + remote);

        PullResult pullOpResult = new PullResult();
        TransferSummary fetchResult = command(FetchOp.class)//
                .addRemote(remote)//
                .setDepth(depth.or(0))//
                .setFullDepth(fullDepth)//
                .setAllRemotes(all)//
                .setFetchIndexes(includeIndexes)//
                .setProgressListener(getProgressListener())//
                .call();

        pullOpResult.setFetchResult(fetchResult);
        pullOpResult.setOldRef(currentBranch);
        pullOpResult.setRemote(suppliedRemote);

        for (LocalRemoteRefSpec fetchspec : remote.getFetchSpecs()) {
            final String localRemoteRefName = fetchspec.getLocal();
            final Optional<Ref> localRemoteRefOpt = command(RefParse.class)
                    .setName(localRemoteRefName).call();
            if (!localRemoteRefOpt.isPresent()) {
                continue;
            }
            final Ref localRemoteRef = localRemoteRefOpt.get();
            if (rebase) {
                getProgressListener().setDescription("Pull: rebasing...");

                // () -> localRemoteRef.getObjectId()
                Supplier<ObjectId> fn = new Supplier<ObjectId>() {
                    @Override
                    public ObjectId get() {
                        return localRemoteRef.getObjectId();
                    }
                };
                command(RebaseOp.class).setUpstream(fn).call();
            } else {
                getProgressListener().setDescription("Pull: merging...");
                String message = this.message;
                if (noFastForward && Strings.isNullOrEmpty(message)) {
                    message = String.format("Pull changes from %s:%s onto %s", remote.getName(),
                            fetchspec.getRemote(), fetchspec.getLocal());
                }
                try {
                    MergeReport report = command(MergeOp.class)
                            .setAuthor(authorName.orNull(), authorEmail.orNull())//
                            .addCommit(localRemoteRef.getObjectId())//
                            .setNoFastForward(noFastForward)//
                            .setMessage(message)//
                            .setProgressListener(getProgressListener())//
                            .call();
                    pullOpResult.setMergeReport(Optional.of(report));
                } catch (NothingToCommitException e) {
                    // the branch that we are trying to pull has less history than the
                    // branch we are pulling into
                }
            }
            final Ref currentBranchFinalState = resolveCurrentBranch();
            pullOpResult.setNewRef(currentBranchFinalState);
        }

        getProgressListener().setDescription("Pull: finished pulling " + remote);
        getProgressListener().complete();

        return pullOpResult;
    }

    private Ref resolveCurrentBranch() {
        return command(BranchResolveOp.class).call().orElseThrow(
                () -> new IllegalStateException("Repository has no HEAD, can't pull."));
    }
}
