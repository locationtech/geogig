/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.porcelain;

import java.io.IOException;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.GlobalContextBuilder;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.Remote;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.LsRemote;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.api.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.remote.IRemoteRepo;
import org.locationtech.geogig.remote.RemoteUtils;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;

/**
 * Clones a remote repository to a given directory.
 * 
 */
public class CloneOp extends AbstractGeoGigOp<Void> {

    private Optional<String> branch = Optional.absent();

    private String repositoryURL;

    private String username = null;

    private String password = null;

    private Optional<Integer> depth = Optional.absent();

    /**
     * @param repositoryURL the URL of the repository to clone
     * @return {@code this}
     */
    public CloneOp setRepositoryURL(final String repositoryURL) {
        this.repositoryURL = repositoryURL;
        return this;
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

    /**
     * Executes the clone operation.
     * 
     * @return {@code null}
     * @see org.locationtech.geogig.api.AbstractGeoGigOp#call()
     */
    @Override
    protected  Void _call() {
        Preconditions.checkArgument(repositoryURL != null && !repositoryURL.isEmpty(),
                "No repository specified to clone from.");
        Repository repository = repository();
        if (repository.isSparse()) {
            Preconditions
                    .checkArgument(branch.isPresent(), "No branch specified for sparse clone.");
        }

        getProgressListener().started();
        getProgressListener().setProgress(0.f);

        // Set up origin
        Remote remote = command(RemoteAddOp.class).setName("origin").setURL(repositoryURL)
                .setMapped(repository.isSparse()).setUserName(username).setPassword(password)
                .setBranch(repository.isSparse() ? branch.get() : null).call();

        if (!depth.isPresent()) {
            // See if we are cloning a shallow clone. If so, a depth must be specified.
            Optional<IRemoteRepo> remoteRepo = RemoteUtils.newRemote(
                    GlobalContextBuilder.builder.build(Hints.readOnly()), remote, repository,
                    repository.deduplicationService());

            Preconditions.checkState(remoteRepo.isPresent(), "Failed to connect to the remote.");
            IRemoteRepo remoteRepoInstance = remoteRepo.get();
            try {
                remoteRepoInstance.open();
            } catch (IOException e) {
                Throwables.propagate(e);
            }
            try {
                depth = remoteRepoInstance.getDepth();
            } finally {
                try {
                    remoteRepoInstance.close();
                } catch (IOException e) {
                    Throwables.propagate(e);
                }
            }
        }

        if (depth.isPresent()) {
            command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET).setScope(ConfigScope.LOCAL)
                    .setName(Repository.DEPTH_CONFIG_KEY).setValue(depth.get().toString()).call();
        }

        // Fetch remote data
        command(FetchOp.class).setDepth(depth.or(0)).setProgressListener(subProgress(90.f)).call();

        // Set up remote tracking branches
        final ImmutableSet<Ref> remoteRefs = command(LsRemote.class).retrieveTags(false)
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
            if (!command(RefParse.class).setName(Ref.HEADS_PREFIX + branchName).call().isPresent()) {
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
        getProgressListener().setProgress(95.f);

        if (!emptyRepo) {
            // checkout branch
            if (branch.isPresent()) {
                command(CheckoutOp.class).setForce(true).setSource(branch.get()).call();
            } else {
                // checkout the head
                final Optional<Ref> currRemoteHead = command(RefParse.class).setName(
                        Ref.REMOTES_PREFIX + remote.getName() + "/" + Ref.HEAD).call();
                if (currRemoteHead.isPresent() && currRemoteHead.get() instanceof SymRef) {
                    final SymRef remoteHeadRef = (SymRef) currRemoteHead.get();
                    final String currentBranch = Ref.localName(remoteHeadRef.getTarget());
                    command(CheckoutOp.class).setForce(true).setSource(currentBranch).call();
                } else {
                    // just leave at default; should be master since we just initialized the repo.
                }

            }
        }

        getProgressListener().complete();

        return null;
    }
}
