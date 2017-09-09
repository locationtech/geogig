/* Copyright (c) 2012-2016 Boundless and others.
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
import static com.google.common.base.Preconditions.checkState;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.porcelain.BranchCreateOp;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.remotes.internal.RemoteResolver;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;

/**
 * Clones a remote repository to a given directory.
 * 
 */
public class CloneOp extends AbstractGeoGigOp<Void> {

    private Optional<String> branch = Optional.absent();

    private String remoteURI;

    private String username = null;

    private String password = null;

    private String remoteName = NodeRef.nodeFromPath(Ref.ORIGIN);

    private Optional<Integer> depth = Optional.absent();

    /**
     * @param repositoryURL the URL of the repository to clone
     * @return {@code this}"pori
     */
    public CloneOp setRepositoryURL(final String repositoryURL) {
        this.remoteURI = repositoryURL;
        return this;
    }

    /**
     * Get the repository URL to be cloned
     */
    public Optional<String> getRepositoryURL() {
        return Optional.fromNullable(remoteURI);
    }

    /**
     * @param username user name for the repository
     * @return {@code this}
     */
    public CloneOp setUserName(String username) {
        this.username = username;
        return this;
    }

    /**
     * @param password password for the repository
     * @return {@code this}
     */
    public CloneOp setPassword(String password) {
        this.password = password;
        return this;
    }

    /**
     * @param branch the branch to checkout when the clone is complete
     * @return {@code this}
     */
    public CloneOp setBranch(@Nullable String branch) {
        this.branch = Optional.fromNullable(branch);
        return this;
    }

    public Optional<String> getBranch() {
        return branch;
    }

    /**
     * @param depth the depth of the clone, if depth is < 1, then a full clone s performed
     * @return {@code this}
     */
    public CloneOp setDepth(final int depth) {
        if (depth > 0) {
            this.depth = Optional.of(depth);
        }
        return this;
    }

    public Optional<Integer> getDepth() {
        return depth;
    }

    /**
     * Executes the clone operation.
     * 
     * @return {@code null}
     * @see org.locationtech.geogig.repository.AbstractGeoGigOp#call()
     */
    @Override
    protected Void _call() {
        checkArgument(!Strings.isNullOrEmpty(remoteURI), "No repository specified to clone from.");
        Repository localRepo = repository();
        if (localRepo.isSparse()) {
            checkArgument(branch.isPresent(), "No branch specified for sparse clone.");
        }

        ProgressListener progressListener = getProgressListener();
        progressListener.started();

        // Set up origin
        Remote remote = command(RemoteAddOp.class).setName(remoteName).setURL(remoteURI)
                .setMapped(localRepo.isSparse()).setUserName(username).setPassword(password)
                .setBranch(localRepo.isSparse() ? branch.get() : null).call();

        if (!depth.isPresent()) {
            // See if we are cloning a shallow clone. If so, a depth must be specified.
            Optional<IRemoteRepo> remoteRepo = getRemote(localRepo, remote);

            checkState(remoteRepo.isPresent(), "Failed to connect to the remote.");
            IRemoteRepo remoteRepoInstance = remoteRepo.get();
            try {
                remoteRepoInstance.open();
            } catch (RepositoryConnectionException e) {
                Throwables.propagate(e);
            }
            try {
                depth = remoteRepoInstance.getDepth();
            } finally {
                remoteRepoInstance.close();
            }
        }

        if (depth.isPresent()) {
            String depthVal = depth.get().toString();
            command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setScope(ConfigScope.LOCAL)
                    .setName(Repository.DEPTH_CONFIG_KEY).setValue(depthVal).call();
        }

        // Fetch remote data
        command(FetchOp.class).addRemote(remote.getName()).setDepth(depth.or(0))
                .setProgressListener(progressListener).call();

        // Set up remote tracking branches
        final ImmutableSet<Ref> remoteRefs = command(LsRemoteOp.class).retrieveTags(false)
                .setRemote(Suppliers.ofInstance(Optional.of(remote))).call();

        boolean emptyRepo = true;

        for (Ref remoteRef : remoteRefs) {
            if (emptyRepo && !remoteRef.getObjectId().isNull()) {
                emptyRepo = false;
            }
            String branchName = remoteRef.localName();
            if (remoteRef instanceof SymRef) {
                continue;
            }
            if (!command(RefParse.class).setName(Ref.HEADS_PREFIX + branchName).call()
                    .isPresent()) {
                command(BranchCreateOp.class).setName(branchName)
                        .setSource(remoteRef.getObjectId().toString()).call();
            } else {
                command(UpdateRef.class).setName(Ref.HEADS_PREFIX + branchName)
                        .setNewValue(remoteRef.getObjectId()).call();
            }

            command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setScope(ConfigScope.LOCAL)
                    .setName("branches." + branchName + ".remote").setValue(remote.getName())
                    .call();

            command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setScope(ConfigScope.LOCAL)
                    .setName("branches." + branchName + ".merge")
                    .setValue(Ref.HEADS_PREFIX + remoteRef.localName()).call();
        }

        if (!emptyRepo) {
            // checkout branch
            if (branch.isPresent()) {
                command(CheckoutOp.class).setForce(true).setSource(branch.get()).call();
            } else {
                // checkout the head
                final Optional<Ref> currRemoteHead = command(RefParse.class)
                        .setName(Ref.REMOTES_PREFIX + remote.getName() + "/" + Ref.HEAD).call();
                if (currRemoteHead.isPresent() && currRemoteHead.get() instanceof SymRef) {
                    final SymRef remoteHeadRef = (SymRef) currRemoteHead.get();
                    final String currentBranch = Ref.localName(remoteHeadRef.getTarget());
                    command(CheckoutOp.class).setForce(true).setSource(currentBranch).call();
                } else {
                    // just leave at default; should be master since we just initialized the repo.
                }

            }
        }

        progressListener.complete();

        return null;
    }

    @VisibleForTesting
    public Optional<IRemoteRepo> getRemote(Repository localRepo, Remote remote) {
        return RemoteResolver.newRemote(localRepo, remote, Hints.readOnly());
    }
}
