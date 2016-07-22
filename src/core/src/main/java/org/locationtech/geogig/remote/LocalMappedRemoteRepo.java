/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remote;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;

import org.locationtech.geogig.api.CommitBuilder;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.AutoCloseableIterator;
import org.locationtech.geogig.api.plumbing.CheckSparsePath;
import org.locationtech.geogig.api.plumbing.FindCommonAncestor;
import org.locationtech.geogig.api.plumbing.ForEachRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.ResolveTreeish;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;
import org.locationtech.geogig.api.plumbing.WriteTree;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.porcelain.DiffOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;

/**
 * An implementation of a remote repository that exists on the local machine.
 * 
 * @see IRemoteRepo
 */
public class LocalMappedRemoteRepo extends AbstractMappedRemoteRepo {

    private Repository remoteRepo;

    private URI remoteRepoLocation;

    /**
     * Constructs a new {@code MappedLocalRemoteRepo} with the given parameters.
     * 
     * @param injector the Guice injector for the new repository
     * @param remoteRepoLocation the directory of the remote repository
     */
    public LocalMappedRemoteRepo(URI remoteRepoLocation, Repository localRepository) {
        super(localRepository);
        this.remoteRepoLocation = remoteRepoLocation;
    }

    /**
     * @param remoteRepo manually set a repository representing this remote repository
     */
    @VisibleForTesting
    public void setRepository(Repository remoteRepo) {
        this.remoteRepo = remoteRepo;
    }

    @Override
    public void open() throws RepositoryConnectionException {
        if (remoteRepo == null) {
            remoteRepo = RepositoryResolver.load(remoteRepoLocation);
        }
    }

    @Override
    public void close() {
        if (remoteRepo != null) {
            remoteRepo.close();
            remoteRepo = null;
        }
    }

    /**
     * @return the remote's HEAD {@link Ref}.
     */
    @Override
    public Ref headRef() {
        final Optional<Ref> currHead = remoteRepo.command(RefParse.class).setName(Ref.HEAD).call();
        Preconditions.checkState(currHead.isPresent(), "Remote repository has no HEAD.");
        return currHead.get();
    }

    /**
     * List the remote's {@link Ref refs}.
     * 
     * @param getHeads whether to return refs in the {@code refs/heads} namespace
     * @param getTags whether to return refs in the {@code refs/tags} namespace
     * @return an immutable set of refs from the remote
     */
    @Override
    public ImmutableSet<Ref> listRefs(final boolean getHeads, final boolean getTags) {
        Predicate<Ref> filter = new Predicate<Ref>() {
            @Override
            public boolean apply(Ref input) {
                boolean keep = false;
                if (getHeads) {
                    keep = input.getName().startsWith(Ref.HEADS_PREFIX);
                }
                if (getTags) {
                    keep = keep || input.getName().startsWith(Ref.TAGS_PREFIX);
                }
                return keep;
            }
        };

        ImmutableSet<Ref> remoteRefs = remoteRepo.command(ForEachRef.class).setFilter(filter)
                .call();

        // Translate the refs to their mapped values.
        ImmutableSet.Builder<Ref> builder = new ImmutableSet.Builder<Ref>();
        for (Ref remoteRef : remoteRefs) {
            Ref newRef = remoteRef;
            if (!(newRef instanceof SymRef)
                    && localRepository.graphDatabase().exists(remoteRef.getObjectId())) {
                ObjectId mappedCommit = localRepository.graphDatabase().getMapping(
                        remoteRef.getObjectId());
                if (mappedCommit != null) {
                    newRef = new Ref(remoteRef.getName(), mappedCommit);
                }
            }
            builder.add(newRef);
        }
        return builder.build();
    }

    /**
     * Delete the given refspec from the remote repository.
     * 
     * @param refspec the refspec to delete
     */
    @Override
    public Optional<Ref> deleteRef(String refspec) {
        Optional<Ref> deletedRef = remoteRepo.command(UpdateRef.class).setName(refspec)
                .setDelete(true).call();
        return deletedRef;
    }

    /**
     * Gets the remote ref that matches the provided ref spec.
     * 
     * @param refspec the refspec to parse
     * @return the matching {@link Ref} or {@link Optional#absent()} if the ref could not be found
     */
    @Override
    protected Optional<Ref> getRemoteRef(String refspec) {
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
    @Override
    protected Optional<Ref> updateRemoteRef(String refspec, ObjectId commitId, boolean delete) {
        Optional<Ref> updatedRef = remoteRepo.command(UpdateRef.class).setName(refspec)
                .setNewValue(commitId).setDelete(delete).call();

        if (updatedRef.isPresent()) {
            final Ref remoteHead = headRef();
            if (remoteHead instanceof SymRef) {
                if (((SymRef) remoteHead).getTarget().equals(updatedRef.get().getName())) {
                    remoteRepo.command(UpdateSymRef.class).setName(Ref.HEAD)
                            .setNewValue(updatedRef.get().getName()).call();
                    RevCommit commit = remoteRepo.getCommit(commitId);
                    remoteRepo.workingTree().updateWorkHead(commit.getTreeId());
                    remoteRepo.index().updateStageHead(commit.getTreeId());
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
    protected void pushSparseCommit(ObjectId commitId) {
        Repository from = localRepository;
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
                            newParents.add(0, from.graphDatabase().getMapping(parentId));
                            continue;
                        }
                    }
                }
                newParents.add(from.graphDatabase().getMapping(parentId));
            }
            if (newParents.size() > 0) {
                parent = from.graphDatabase().getMapping(newParents.get(0));
            }
            try (AutoCloseableIterator<DiffEntry> diffIter = from.command(DiffOp.class)
                    .setNewVersion(commitId).setOldVersion(parent).setReportTrees(true).call()) {
                LocalCopyingDiffIterator changes = new LocalCopyingDiffIterator(diffIter, from, to);

                RevTree rootTree = RevTree.EMPTY;

                if (newParents.size() > 0) {
                    ObjectId mappedCommit = newParents.get(0);

                    Optional<ObjectId> treeId = to.command(ResolveTreeish.class)
                            .setTreeish(mappedCommit).call();
                    if (treeId.isPresent()) {
                        rootTree = to.getTree(treeId.get());
                    }
                }

                // Create new commit
                ObjectId newTreeId = to.command(WriteTree.class)
                        .setOldRoot(Suppliers.ofInstance(rootTree))
                        .setDiffSupplier(
                                Suppliers.ofInstance((AutoCloseableIterator<DiffEntry>) changes))
                        .call();

                CommitBuilder builder = new CommitBuilder(commit);
                builder.setParentIds(newParents);
                builder.setTreeId(newTreeId);

                RevCommit mapped = builder.build();
                to.objectDatabase().put(mapped);

                from.graphDatabase().map(commit.getId(), mapped.getId());
                from.graphDatabase().map(mapped.getId(), commit.getId());
            }

        }
    }

    /**
     * @return the {@link RepositoryWrapper} for this remote
     */
    @Override
    public RepositoryWrapper getRemoteWrapper() {
        return new LocalRepositoryWrapper(remoteRepo);
    }

    /**
     * Retrieves an object with the specified id from the remote.
     * 
     * @param objectId the object to get
     * @return the fetched object
     */
    @Override
    protected Optional<RevObject> getObject(ObjectId objectId) {
        return remoteRepo.command(RevObjectParse.class).setObjectId(objectId).call();
    }

    /**
     * Gets all of the changes from the target commit that should be applied to the sparse clone.
     * 
     * @param commit the commit to get changes from
     * @return an iterator for changes that match the repository filter
     */
    @Override
    protected FilteredDiffIterator getFilteredChanges(RevCommit commit) {
        ObjectId parent = ObjectId.NULL;
        if (commit.getParentIds().size() > 0) {
            parent = commit.getParentIds().get(0);
        }

        AutoCloseableIterator<DiffEntry> changes = remoteRepo.command(DiffOp.class)
                .setNewVersion(commit.getId()).setOldVersion(parent).setReportTrees(true).call();

        return new LocalFilteredDiffIterator(changes, remoteRepo, localRepository, filter);
    }

    /**
     * Gets the depth of the remote repository.
     * 
     * @return the depth of the repository, or {@link Optional#absent()} if the repository is not
     *         shallow
     */
    @Override
    public Optional<Integer> getDepth() {
        return remoteRepo.getDepth();
    }
}
