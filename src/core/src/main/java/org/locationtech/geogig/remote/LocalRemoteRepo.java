/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remote;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.ForEachRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;
import org.locationtech.geogig.api.porcelain.SynchronizationException;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.ObjectInserter;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;

/**
 * An implementation of a remote repository that exists on the local machine.
 * 
 * @see IRemoteRepo
 */
class LocalRemoteRepo extends AbstractRemoteRepo {

    private GeoGIG remoteGeoGig;

    private Context injector;

    private File workingDirectory;

    /**
     * Constructs a new {@code LocalRemoteRepo} with the given parameters.
     * 
     * @param injector the Guice injector for the new repository
     * @param workingDirectory the directory of the remote repository
     */
    public LocalRemoteRepo(Context injector, File workingDirectory, Repository localRepository) {
        super(localRepository);
        this.injector = injector;
        this.workingDirectory = workingDirectory;
    }

    /**
     * @param geogig manually set a geogig for this remote repository
     */
    public void setGeoGig(GeoGIG geogig) {
        this.remoteGeoGig = geogig;
    }

    /**
     * Opens the remote repository.
     * 
     * @throws IOException
     */
    @Override
    public void open() throws IOException {
        if (remoteGeoGig == null) {
            remoteGeoGig = new GeoGIG(injector, workingDirectory);
            remoteGeoGig.getRepository();
        }

    }

    /**
     * Closes the remote repository.
     * 
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        remoteGeoGig.close();

    }

    /**
     * @return the remote's HEAD {@link Ref}.
     */
    @Override
    public Ref headRef() {
        final Optional<Ref> currHead = remoteGeoGig.command(RefParse.class).setName(Ref.HEAD)
                .call();
        Preconditions.checkState(currHead.isPresent(), "Remote repository has no HEAD.");
        if (currHead.get().getObjectId().equals(ObjectId.NULL)) {
            return null;
        } else {
            return currHead.get();
        }
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
                if (input.getObjectId().equals(ObjectId.NULL)) {
                    return false;
                }
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
        return remoteGeoGig.command(ForEachRef.class).setFilter(filter).call();
    }

    /**
     * Fetch all new objects from the specified {@link Ref} from the remote.
     * 
     * @param ref the remote ref that points to new commit data
     * @param fetchLimit the maximum depth to fetch
     */
    @Override
    public void fetchNewData(Ref ref, Optional<Integer> fetchLimit) {

        CommitTraverser traverser = getFetchTraverser(fetchLimit);

        try {
            traverser.traverse(ref.getObjectId());
            while (!traverser.commits.isEmpty()) {
                walkHead(traverser.commits.pop(), true);
            }

        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    /**
     * Push all new objects from the specified {@link Ref} to the given refspec.
     * 
     * @param ref the local ref that points to new commit data
     * @param refspec the refspec to push to
     */
    @Override
    public void pushNewData(Ref ref, String refspec) throws SynchronizationException {
        Optional<Ref> remoteRef = remoteGeoGig.command(RefParse.class).setName(refspec).call();
        remoteRef = remoteRef.or(remoteGeoGig.command(RefParse.class)
                .setName(Ref.TAGS_PREFIX + refspec).call());
        checkPush(ref, remoteRef);

        CommitTraverser traverser = getPushTraverser(remoteRef);

        traverser.traverse(ref.getObjectId());
        while (!traverser.commits.isEmpty()) {
            walkHead(traverser.commits.pop(), false);
        }

        String nameToSet = remoteRef.isPresent() ? remoteRef.get().getName() : Ref.HEADS_PREFIX
                + refspec;

        Ref updatedRef = remoteGeoGig.command(UpdateRef.class).setName(nameToSet)
                .setNewValue(ref.getObjectId()).call().get();

        Ref remoteHead = headRef();
        if (remoteHead instanceof SymRef) {
            if (((SymRef) remoteHead).getTarget().equals(updatedRef.getName())) {
                remoteGeoGig.command(UpdateSymRef.class).setName(Ref.HEAD)
                        .setNewValue(ref.getName()).call();
                RevCommit commit = remoteGeoGig.getRepository().getCommit(ref.getObjectId());
                remoteGeoGig.getRepository().workingTree().updateWorkHead(commit.getTreeId());
                remoteGeoGig.getRepository().index().updateStageHead(commit.getTreeId());
            }
        }
    }

    /**
     * Delete the given refspec from the remote repository.
     * 
     * @param refspec the refspec to delete
     */
    @Override
    public void deleteRef(String refspec) {
        remoteGeoGig.command(UpdateRef.class).setName(refspec).setDelete(true).call();
    }

    protected void walkHead(ObjectId headId, boolean fetch) {
        Repository from = localRepository;
        Repository to = remoteGeoGig.getRepository();
        if (fetch) {
            Repository tmp = to;
            to = from;
            from = tmp;
        }
        ObjectInserter objectInserter = to.newObjectInserter();
        Optional<RevObject> object = from.command(RevObjectParse.class).setObjectId(headId).call();

        if (object.isPresent()) {
            if (object.get().getType().equals(TYPE.COMMIT)) {
                RevCommit commit = (RevCommit) object.get();
                walkTree(commit.getTreeId(), from, to, objectInserter);
            } else if (object.get().getType().equals(TYPE.TAG)) {
                RevTag tag = (RevTag) object.get();
                walkCommit(tag.getCommitId(), from, to, objectInserter);
            }
            objectInserter.insert(object.get());
        }
    }

    protected void walkCommit(ObjectId commitId, Repository from, Repository to,
            ObjectInserter objectInserter) {
        Optional<RevObject> object = from.command(RevObjectParse.class).setObjectId(commitId)
                .call();
        if (object.isPresent() && object.get().getType().equals(TYPE.COMMIT)) {
            RevCommit commit = (RevCommit) object.get();
            walkTree(commit.getTreeId(), from, to, objectInserter);

            objectInserter.insert(commit);
        }
    }

    private void walkTree(ObjectId treeId, Repository from, Repository to,
            ObjectInserter objectInserter) {
        // See if we already have it
        if (to.objectDatabase().exists(treeId)) {
            return;
        }

        Optional<RevObject> object = from.command(RevObjectParse.class).setObjectId(treeId).call();
        if (object.isPresent() && object.get().getType().equals(TYPE.TREE)) {
            RevTree tree = (RevTree) object.get();

            objectInserter.insert(tree);
            // walk subtrees
            if (tree.buckets().isPresent()) {
                for (Bucket bucket : tree.buckets().get().values()) {
                    walkTree(bucket.id(), from, to, objectInserter);
                }
            } else {
                // get new objects
                for (Iterator<Node> children = tree.children(); children.hasNext();) {
                    Node ref = children.next();
                    moveObject(ref.getObjectId(), from, to, objectInserter);
                    ObjectId metadataId = ref.getMetadataId().or(ObjectId.NULL);
                    if (!metadataId.isNull()) {
                        moveObject(metadataId, from, to, objectInserter);
                    }
                }
            }
        }
    }

    private void moveObject(ObjectId objectId, Repository from, Repository to,
            ObjectInserter objectInserter) {
        // See if we already have it
        if (to.objectDatabase().exists(objectId)) {
            return;
        }

        Optional<RevObject> childObject = from.command(RevObjectParse.class).setObjectId(objectId)
                .call();
        if (childObject.isPresent()) {
            RevObject revObject = childObject.get();
            if (TYPE.TREE.equals(revObject.getType())) {
                walkTree(objectId, from, to, objectInserter);
            }
            objectInserter.insert(revObject);
        }
    }

    /**
     * @return the {@link RepositoryWrapper} for this remote
     */
    @Override
    public RepositoryWrapper getRemoteWrapper() {
        return new LocalRepositoryWrapper(remoteGeoGig.getRepository());
    }

    /**
     * Gets the depth of the remote repository.
     * 
     * @return the depth of the repository, or {@link Optional#absent()} if the repository is not
     *         shallow
     */
    @Override
    public Optional<Integer> getDepth() {
        return remoteGeoGig.getRepository().getDepth();
    }
}
