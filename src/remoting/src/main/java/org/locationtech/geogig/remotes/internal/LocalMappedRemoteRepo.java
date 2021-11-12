/* Copyright (c) 2013-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remotes.internal;

import static org.locationtech.geogig.base.Preconditions.checkState;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.CheckSparsePath;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.ForEachRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.plumbing.WriteTree;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.repository.Remote;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryFinder;
import org.locationtech.geogig.repository.impl.RepositoryFilter;
import org.locationtech.geogig.repository.impl.RepositoryImpl;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.GraphDatabase;

import com.google.common.annotations.VisibleForTesting;

/**
 * An implementation of a remote repository that exists on the local machine.
 * 
 * @see IRemoteRepo
 */
public class LocalMappedRemoteRepo extends AbstractMappedRemoteRepo {

    private Repository remoteRepo;

    private URI remoteRepoLocation;

    /**
     * @param remoteRepoLocation the directory of the remote repository
     */
    public LocalMappedRemoteRepo(Remote remote, URI remoteRepoLocation) {
        super(remote);
        this.remoteRepoLocation = remoteRepoLocation;
    }

    /**
     * @param remoteRepo manually set a repository representing this remote repository
     */
    @VisibleForTesting
    public void setRepository(Repository remoteRepo) {
        this.remoteRepo = remoteRepo;
    }

    public @Override void open() throws RepositoryConnectionException {
        if (remoteRepo == null) {
            remoteRepo = RepositoryFinder.INSTANCE.open(remoteRepoLocation);
        }
    }

    public @Override void close() {
        if (remoteRepo != null) {
            remoteRepo.close();
            remoteRepo = null;
        }
    }

    /**
     * @return the remote's HEAD {@link Ref}.
     */
    public @Override Optional<Ref> headRef() {
        final Optional<Ref> currHead = remoteRepo.command(RefParse.class).setName(Ref.HEAD).call();
        return currHead;
    }

    /**
     * List the remote's {@link Ref refs}.
     * 
     * @param getHeads whether to return refs in the {@code refs/heads} namespace
     * @param getTags whether to return refs in the {@code refs/tags} namespace
     * @return an immutable set of refs from the remote
     */
    public @Override Set<Ref> listRefs(final Repository local, final boolean getHeads,
            final boolean getTags) {
        Predicate<Ref> filter = input -> {
            boolean keep = false;
            if (getHeads) {
                keep = input.getName().startsWith(Ref.HEADS_PREFIX);
            }
            if (getTags) {
                keep = keep || input.getName().startsWith(Ref.TAGS_PREFIX);
            }
            return keep;
        };

        Set<Ref> remoteRefs = remoteRepo.command(ForEachRef.class).setFilter(filter).call();

        // Translate the refs to their mapped values.
        Set<Ref> refs = new TreeSet<>();
        for (Ref remoteRef : remoteRefs) {
            Ref newRef = remoteRef;
            GraphDatabase graphdb = local.context().graphDatabase();
            ObjectId headId = remoteRef.getObjectId();
            if (!(newRef instanceof SymRef) && graphdb.exists(headId)) {
                ObjectId mappedCommit = graphdb.getMapping(headId);
                if (mappedCommit != null) {
                    newRef = new Ref(remoteRef.getName(), mappedCommit);
                }
            }
            refs.add(newRef);
        }
        return refs;
    }

    /**
     * Delete the given refspec from the remote repository.
     * 
     * @param refspec the refspec to delete
     */
    public @Override Optional<Ref> deleteRef(String refspec) {
        Optional<Ref> deletedRef = remoteRepo.command(UpdateRef.class).setName(refspec)
                .setDelete(true).call();
        return deletedRef;
    }

    /**
     * Gets the remote ref that matches the provided ref spec.
     * 
     * @param refspec the refspec to parse
     * @return the matching {@link Ref} or {@link Optional#empty()} if the ref could not be found
     */
    protected @Override Optional<Ref> getRemoteRef(String refspec) {
        return remoteRepo.command(RefParse.class).setName(refspec).call();
    }

    /**
     * Updates the remote ref that matches the given refspec.
     * 
     * @param refspec the ref to update
     * @param commitId the new value of the ref
     * @param delete if true, the remote ref will be deleted
     * @return the updated ref
     */
    protected @Override Optional<Ref> updateRemoteRef(String refspec, ObjectId commitId,
            boolean delete) {
        Optional<Ref> updatedRef = remoteRepo.command(UpdateRef.class).setName(refspec)
                .setNewValue(commitId).setDelete(delete).call();

        if (updatedRef.isPresent()) {
            final Ref remoteHead = headRef().orElse(null);
            if (remoteHead instanceof SymRef) {
                if (((SymRef) remoteHead).getTarget().equals(updatedRef.get().getName())) {
                    remoteRepo.command(UpdateSymRef.class).setName(Ref.HEAD)
                            .setNewValue(updatedRef.get().getName()).call();
                    RevCommit commit = remoteRepo.context().objectDatabase().getCommit(commitId);
                    remoteRepo.context().workingTree().updateWorkHead(commit.getTreeId());
                    remoteRepo.context().stagingArea().updateStageHead(commit.getTreeId());
                }
            }
        }
        return updatedRef;
    }

    /**
     * This function takes all of the changes introduced by a commit on the sparse repository and
     * creates a new commit on the full repository with those changes.
     * 
     * @param commitId the commit id of commit from the sparse repository
     * @param from the sparse repository
     * @param to the full repository
     */
    protected @Override void pushSparseCommit(final Repository local, ObjectId commitId) {
        Repository from = local;
        Repository to = remoteRepo;
        Optional<RevObject> object = from.command(RevObjectParse.class).setObjectId(commitId)
                .call();
        if (object.isPresent() && object.get().getType().equals(TYPE.COMMIT)) {
            RevCommit commit = (RevCommit) object.get();
            ObjectId parent = ObjectId.NULL;
            List<ObjectId> newParents = new LinkedList<ObjectId>();
            for (int i = 0; i < commit.getParentIds().size(); i++) {
                ObjectId parentId = commit.getParentIds().get(i);
                if (i != 0) {
                    Optional<ObjectId> commonAncestor = from.command(FindCommonAncestor.class)
                            .setLeftId(commit.getParentIds().get(0)).setRightId(parentId).call();
                    if (commonAncestor.isPresent()) {
                        if (from.command(CheckSparsePath.class).setStart(parentId)
                                .setEnd(commonAncestor.get()).call()) {
                            // This should be the base commit to preserve the sparse changes that
                            // were filtered
                            // out.
                            newParents.add(0, from.context().graphDatabase().getMapping(parentId));
                            continue;
                        }
                    }
                }
                newParents.add(from.context().graphDatabase().getMapping(parentId));
            }
            if (newParents.size() > 0) {
                parent = from.context().graphDatabase().getMapping(newParents.get(0));
            }
            try (AutoCloseableIterator<DiffEntry> diffIter = from.command(DiffOp.class)
                    .setNewVersion(commitId).setOldVersion(parent).setReportTrees(true).call()) {

                @SuppressWarnings("resource")
                LocalCopyingDiffIterator changes = new LocalCopyingDiffIterator(diffIter, from, to);

                final RevTree rootTree;
                if (newParents.size() > 0) {
                    ObjectId mappedCommit = newParents.get(0);

                    Optional<ObjectId> treeId = to.command(ResolveTreeish.class)
                            .setTreeish(mappedCommit).call();
                    if (treeId.isPresent()) {
                        rootTree = to.context().objectDatabase().getTree(treeId.get());
                    } else {
                        rootTree = RevTree.EMPTY;
                    }
                } else {
                    rootTree = RevTree.EMPTY;
                }

                // Create new commit
                ObjectId newTreeId = to.command(WriteTree.class).setOldRoot(() -> rootTree)
                        .setDiffSupplier(() -> changes).call();

                RevCommitBuilder builder = RevCommit.builder().init(commit);
                builder.parentIds(newParents);
                builder.treeId(newTreeId);

                RevCommit mapped = builder.build();
                to.context().objectDatabase().put(mapped);

                from.context().graphDatabase().map(commit.getId(), mapped.getId());
                from.context().graphDatabase().map(mapped.getId(), commit.getId());
            }

        }
    }

    /**
     * @return the {@link RepositoryWrapper} for this remote
     */
    public @Override RepositoryWrapper getRemoteWrapper() {
        return new LocalRepositoryWrapper(remoteRepo);
    }

    /**
     * Retrieves an object with the specified id from the remote.
     * 
     * @param objectId the object to get
     * @return the fetched object
     */
    protected @Override Optional<RevObject> getObject(ObjectId objectId) {
        return remoteRepo.command(RevObjectParse.class).setObjectId(objectId).call();
    }

    protected @Override FilteredDiffIterator getFilteredChanges(final Repository local,
            RevCommit commit) {
        ObjectId parent = ObjectId.NULL;
        if (commit.getParentIds().size() > 0) {
            parent = commit.getParentIds().get(0);
        }

        AutoCloseableIterator<DiffEntry> changes = remoteRepo.command(DiffOp.class)
                .setNewVersion(commit.getId()).setOldVersion(parent).setReportTrees(true).call();

        Optional<RepositoryFilter> filter = RepositoryImpl.getFilter(local);
        checkState(filter.isPresent(), "No filter found for sparse clone.");
        return new LocalFilteredDiffIterator(changes, remoteRepo, local, filter.get());
    }

    /**
     * Gets the depth of the remote repository.
     * 
     * @return the depth of the repository, or {@link Optional#empty()} if the repository is not
     *         shallow
     */
    public @Override Optional<Integer> getDepth() {
        return remoteRepo.getDepth();
    }
}
