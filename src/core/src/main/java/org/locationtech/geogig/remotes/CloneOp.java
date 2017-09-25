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

import java.net.URI;
import java.util.Collection;
import java.util.Map;

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
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

/**
 * Clones a remote repository to a new repsitory.
 * 
 */
public class CloneOp extends AbstractGeoGigOp<Repository> {

    private Optional<String> branch = Optional.absent();

    private URI remoteURI;

    private URI cloneURI;

    private String username = null;

    private String password = null;

    private String remoteName = NodeRef.nodeFromPath(Ref.ORIGIN);

    private Optional<Integer> depth = Optional.absent();

    @Deprecated
    public CloneOp setRepositoryURL(String uri) {
        this.remoteURI = URI.create(uri);
        return this;
    }

    /**
     * @param repositoryURL the URL of the repository to clone
     * @return {@code this}
     */
    public CloneOp setRemoteURI(final URI repositoryURL) {
        this.remoteURI = repositoryURL;
        return this;
    }

    /**
     * Get the repository URL to be cloned
     */
    public URI getRemoteURI() {
        return remoteURI;
    }

    /**
     * @param uri the URL of the repository to clone
     * @return {@code this}
     */
    public CloneOp setCloneURI(final URI uri) {
        this.cloneURI = uri;
        return this;
    }

    public URI getCloneURI() {
        return cloneURI;
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
     * @return the cloned repository, in an open state
     * @see org.locationtech.geogig.repository.AbstractGeoGigOp#call()
     */
    @Override
    protected Repository _call() {
        checkPreconditions();
        getProgressListener().started();
        final Repository cloneRepo = createClone();
        try {
            // Set up origin
            final Remote remote = addRemote(cloneRepo);
            final Integer depth = this.depth.or(() -> getRemoteDepth(remote));
            setDepth(cloneRepo, depth);

            final Iterable<RefDiff> refs = fetchRemoteData(cloneRepo, remote, depth);
            setUpRemoteTrackingBranches(cloneRepo, remote, refs);

        } catch (Exception e) {
            // something went wrong, need to delete the clone
            cloneRepo.close();

            // TODO: enable when this commands actually creates the
            // try {
            // GeoGIG.delete(cloneURI);
            // } catch (Exception e1) {
            // e1.printStackTrace();
            // }
            throw Throwables.propagate(e);
        }
        getProgressListener().complete();
        return cloneRepo;
    }

    private Integer getRemoteDepth(Remote remote) {
        try (IRemoteRepo remoteRepo = openRemote(remote)) {
            return remoteRepo.getDepth().or(0);
        }
    }

    private Collection<RefDiff> fetchRemoteData(final Repository clone, final Remote remote,
            final int depth) {
        // Fetch remote data
        final TransferSummary fetchResults;
        final String remoteName = remote.getName();
        final ProgressListener progress = getProgressListener();

        fetchResults = clone.command(FetchOp.class)//
                .addRemote(remoteName)//
                .setDepth(depth)//
                .setProgressListener(progress)//
                .call();

        Map<String, Collection<RefDiff>> RefDiffs = fetchResults.getRefDiffs();
        String fetchURL = remote.getFetchURL();
        Collection<RefDiff> refs = RefDiffs.get(fetchURL);
        if (refs == null) {
            refs = ImmutableList.of();
        }
        return refs;
    }

    private void setDepth(Repository clone, int depth) {
        if (depth > 0) {
            String name = Repository.DEPTH_CONFIG_KEY;
            String value = String.valueOf(depth);
            setConfig(clone, name, value);
        }

    }

    private void setConfig(Repository clone, String name, String value) {
        clone.command(ConfigOp.class)//
                .setAction(ConfigAction.CONFIG_SET)//
                .setScope(ConfigScope.LOCAL)//
                .setName(name)//
                .setValue(value).call();
    }

    private void setUpRemoteTrackingBranches(Repository clone, Remote remote,
            Iterable<RefDiff> refs) {

        boolean emptyRepo = true;

        for (RefDiff r : refs) {
            Ref remoteRef = r.getNewRef();

            if (emptyRepo && !remoteRef.getObjectId().isNull()) {
                emptyRepo = false;
            }
            if (remoteRef instanceof SymRef) {
                continue;
            }
            final boolean isBranch = remoteRef.getName().startsWith(Ref.HEADS_PREFIX);
            final boolean isTag = remoteRef.getName().startsWith(Ref.TAGS_PREFIX);
            if (!(isBranch || isTag)) {
                continue;
            }
            final String branchName = remoteRef.localName();
            final String branchRef = (isBranch ? Ref.HEADS_PREFIX : Ref.TAGS_PREFIX) + branchName;
            if (!clone.command(RefParse.class).setName(branchRef).call().isPresent()) {
                clone.command(BranchCreateOp.class).setName(branchName)
                        .setSource(remoteRef.getObjectId().toString()).call();
            } else {
                clone.command(UpdateRef.class).setName(branchRef)
                        .setNewValue(remoteRef.getObjectId()).call();
            }

            if (isBranch) {
                setConfig(clone, "branches." + branchName + ".remote", remote.getName());
                setConfig(clone, "branches." + branchName + ".merge",
                        Ref.HEADS_PREFIX + remoteRef.localName());
            }
        }

        if (!emptyRepo) {
            // checkout branch
            if (branch.isPresent()) {
                clone.command(CheckoutOp.class).setForce(true).setSource(branch.get()).call();
            } else {
                // checkout the head
                final Optional<Ref> currRemoteHead = clone.command(RefParse.class)
                        .setName(Ref.REMOTES_PREFIX + remote.getName() + "/" + Ref.HEAD).call();
                if (currRemoteHead.isPresent() && currRemoteHead.get() instanceof SymRef) {
                    final SymRef remoteHeadRef = (SymRef) currRemoteHead.get();
                    final String currentBranch = Ref.localName(remoteHeadRef.getTarget());
                    clone.command(CheckoutOp.class).setForce(true).setSource(currentBranch).call();
                } else {
                    // just leave at default; should be master since we just initialized the repo.
                }

            }
        }
    }

    private Remote addRemote(Repository clone) {
        final boolean sparse = clone.isSparse();
        if (sparse) {
            checkArgument(this.branch.isPresent(), "No branch specified for sparse clone.");
        }
        {// won't be necessary once ClopeOp actually creates the clone repo
            Optional<Remote> remote = clone.command(RemoteResolve.class).setName(remoteName).call();
            if (remote.isPresent()) {
                clone.command(RemoteRemoveOp.class).setName(remoteName).call();
            }
        }
        final @Nullable String branch = this.branch.orNull();
        Remote remote = clone.command(RemoteAddOp.class)//
                .setName(remoteName)//
                .setURL(remoteURI.toString())//
                .setMapped(sparse)//
                .setUserName(username)//
                .setPassword(password)//
                .setBranch(branch).//
                call();

        return remote;
    }

    private Repository createClone() {
        // TODO: repository shall be created here instead of getting the context's, whish shall not
        // exist
        return repository();
    }

    private void checkPreconditions() throws IllegalArgumentException {
        checkArgument(null != remoteURI, "No repository specified to clone from.");
        // TODO: uncomment once this command creates the clone and cloneURI becomes mandatory
        // checkArgument(null != cloneURI, "Clone repository URI not provided.");
        checkArgument(!remoteURI.equals(cloneURI), "Source and target repositories are the same");
    }

    private IRemoteRepo openRemote(Remote remote) {
        return command(OpenRemote.class).setRemote(remote).readOnly().call();
    }

    public CloneOp setRemoteName(String remoteName) {
        this.remoteName = remoteName;
        return this;
    }
}
