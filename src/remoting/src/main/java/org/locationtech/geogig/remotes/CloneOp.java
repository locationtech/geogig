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
import static com.google.common.base.Preconditions.checkState;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.porcelain.CheckoutOp;
import org.locationtech.geogig.porcelain.ConfigOp;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.porcelain.InitOp;
import org.locationtech.geogig.remotes.internal.IRemoteRepo;
import org.locationtech.geogig.remotes.pack.MapRef;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * Clones a remote repository to a new repository.
 * <p>
 * Cloning is the process of creating a new repository that's an exact copy of another one's local
 * reachable revision history.
 * <p>
 * A repository's local reachable revision history is the set of all {@link RevObject revision
 * objects} reachable from its tips (i.e. branches and tags) that are in its local refs namespace
 * (i.e. those in the {@code refs/heads/*} and {@code refs/tags} namespaces. That is, objects and
 * branches that are only addressed by references to copies of a remote repository (those in
 * {@code refs/remotes/} namespace) are not subject to be cloned, as well as any other object
 * (commits, trees, features, etc) that might exist in the remote repository but are not reachable
 * from any of its tips (for example, because a branch has been deleted).
 * <p>
 * To perform a clone, given an existing repository, the following steps are followed:
 * <ul>
 * <li>Create the new repository (see {@link InitOp});
 * <li>Configure the repository to be cloned a remote in the new repository (see
 * {@link RemoteAddOp}), named {@code origin} by default, a different name can be assigned through
 * {@link #setRemoteName};
 * <li>Get all the contents and remote refs from the remote to the clone (see {@link FetchOp});
 * <li>Finally, for each remote ref created by {@link FetchOp} that represents a branch, create a
 * new local branch ref in the cloned repo (e.g. create a {@code refs/heads/master} matching
 * {@code refs/remotes/origin/master}, and so on);
 * </ul>
 * 
 * @since 1.0
 */
public class CloneOp extends AbstractGeoGigOp<Repository> {

    private static final String DEFAULT_REMOTE_NAME = NodeRef.nodeFromPath(Ref.ORIGIN);

    private Optional<String> branch = Optional.absent();

    private URI remoteURI;

    private URI cloneURI;

    private String username = null;

    private String password = null;

    private String remoteName = DEFAULT_REMOTE_NAME;

    private Optional<Integer> depth = Optional.absent();

    /**
     * Executes the clone operation.
     * 
     * @return the cloned repository, in an open state
     * @see org.locationtech.geogig.repository.AbstractGeoGigOp#call()
     */
    @Override
    protected Repository _call() {
        checkPreconditions();
        final Repository cloneRepo = createClone();
        try {
            // Set up origin
            final Remote remote = addRemote(cloneRepo);
            final Iterable<RefDiff> localRemoteRefs;
            try (IRemoteRepo remoteRepo = openRemote(remote)) {
                final Integer depth = this.depth.or(remoteRepo.getDepth()).or(0);
                setDepth(cloneRepo, depth);
                localRemoteRefs = fetchRemoteData(cloneRepo, remote, depth);
            }
            setUpRemoteTrackingBranches(cloneRepo, remote, localRemoteRefs);
        } catch (Exception e) {
            // something went wrong, need to delete the clone
            cloneRepo.close();

            // TODO: enable when this commands actually creates the
            // try {
            // GeoGIG.delete(cloneURI);
            // } catch (Exception e1) {
            // e1.printStackTrace();
            // }
            Throwables.propagateIfPossible(e, RuntimeException.class);
            throw new RuntimeException(e);
        }
        {
            // Repository source;
            // try {
            // source = RepositoryResolver.load(remoteURI);
            // } catch (RepositoryConnectionException e) {
            // throw Throwables.propagate(e);
            // }
            // TestSupport.verifySameContents(source, cloneRepo);
        }
        return cloneRepo;
    }

    @Deprecated
    public CloneOp setRepositoryURL(String uri) {
        this.remoteURI = URI.create(uri);
        return this;
    }

    /**
     * @param remoteName the name to assign to the remote repository; defaults to {@code origin}
     */
    public CloneOp setRemoteName(String remoteName) {
        this.remoteName = remoteName == null ? DEFAULT_REMOTE_NAME : remoteName;
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

        Map<String, Collection<RefDiff>> changedRefs = fetchResults.getRefDiffs();
        String fetchURL = remote.getFetchURL();
        Collection<RefDiff> refs = changedRefs.get(fetchURL);
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
            Iterable<RefDiff> refdifss) {

        final Iterable<Ref> remoteRefs = Iterables.transform(refdifss, (cr) -> cr.getNewRef());
        final Iterable<Ref> localRefs = command(MapRef.class)//
                .setRemote(remote)//
                .addAll(remoteRefs)//
                .convertToLocal()//
                .call();

        final Set<String> createdBranches = new HashSet<>();
        for (Ref localRef : localRefs) {
            final String refName = localRef.getName();
            final boolean isSymRef = localRef instanceof SymRef;
            final boolean isBranch = !isSymRef && refName.startsWith(Ref.HEADS_PREFIX);

            if (!isSymRef) {
                // can't create branches out of symrefs
                Optional<Ref> ref = command(UpdateRef.class).setName(refName)
                        .setNewValue(localRef.getObjectId()).call();
                checkState(ref.isPresent());
            }

            if (isBranch) {
                String branchName = localRef.localName();
                createdBranches.add(branchName);
                setConfig(clone, "branches." + branchName + ".remote", remote.getName());
                setConfig(clone, "branches." + branchName + ".merge", refName);
            }
        }

        // checkout the head
        String currentBranch = null;
        if (branch.isPresent()) {
            if (createdBranches.contains(branch.get())) {
                currentBranch = branch.get();
            } else {
                String warn = String.format(
                        "WARNING: can't checkout requested branch %s, it does not exist in the remote",
                        branch.get());
                getProgressListener().setDescription(warn);
            }
        } else {
            String remoteHead = Ref.REMOTES_PREFIX + remote.getName() + "/" + Ref.HEAD;
            final @Nullable Ref currRemoteHead = clone.command(RefParse.class).setName(remoteHead)
                    .call().orNull();
            if (currRemoteHead instanceof SymRef) {
                currentBranch = currRemoteHead.peel().localName();
            }
        }
        if (currentBranch != null) {
            clone.command(CheckoutOp.class).setForce(true).setSource(currentBranch).call();
        } else {
            // just leave at default; should be master since we just initialized the repo.
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
}
