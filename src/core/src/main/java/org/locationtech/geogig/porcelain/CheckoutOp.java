/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.locationtech.geogig.model.RevTree.EMPTY;
import static org.locationtech.geogig.model.RevTree.EMPTY_TREE_ID;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.plumbing.UpdateTree;
import org.locationtech.geogig.porcelain.CheckoutException.StatusCode;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ConflictsDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;

/**
 * Updates objects in the working tree to match the version in the index or the specified tree. If
 * no {@link #addPath paths} are given, will also update {@link Ref#HEAD HEAD} to set the specified
 * branch as the current branch, or to the specified commit if the given {@link #setSource origin}
 * is a commit id instead of a branch name, in which case HEAD will be a plain ref instead of a
 * symbolic ref, hence making it a "dettached head".
 */
@CanRunDuringConflict
@Hookable(name = "checkout")
public class CheckoutOp extends AbstractGeoGigOp<CheckoutResult> {

    private String branchOrCommit;

    private Set<String> paths;

    private boolean force = false;

    private boolean ours;

    private boolean theirs;

    public CheckoutOp() {
        paths = Sets.newTreeSet();
    }

    public CheckoutOp setSource(@Nullable final String branchOrCommit) {
        this.branchOrCommit = branchOrCommit;
        return this;
    }

    public CheckoutOp setForce(final boolean force) {
        this.force = force;
        return this;
    }

    public CheckoutOp addPath(final CharSequence path) {
        checkNotNull(path);
        paths.add(path.toString());
        return this;
    }

    public CheckoutOp setOurs(final boolean ours) {
        this.ours = ours;
        return this;
    }

    public CheckoutOp setTheirs(final boolean theirs) {
        this.theirs = theirs;
        return this;
    }

    public CheckoutOp addPaths(final Collection<? extends CharSequence> paths) {
        checkNotNull(paths);
        for (CharSequence path : paths) {
            addPath(path);
        }
        return this;
    }

    /**
     * @return the id of the new work tree
     */
    @Override
    protected CheckoutResult _call() throws CheckoutException {

        checkState(branchOrCommit != null || !paths.isEmpty(),
                "No branch, tree, or path were specified");
        checkArgument(!(ours && theirs), "Cannot use both --ours and --theirs.");
        checkArgument((ours == theirs) || branchOrCommit == null,
                "--ours/--theirs is incompatible with switching branches.");

        CheckoutResult checkoutResult;

        if (paths.isEmpty()) {
            checkoutResult = branchCheckout();
        } else {
            checkoutResult = checkoutFiltered(ImmutableSet.copyOf(paths));
        }
        checkoutResult.setNewTree(workingTree().getTree().getId());
        return checkoutResult;
    }

    private CheckoutResult checkoutFiltered(final Set<String> paths) throws CheckoutException {
        CheckoutResult checkoutResult = new CheckoutResult();
        checkoutResult.setResult(CheckoutResult.Results.UPDATE_OBJECTS);

        final ConflictsDatabase conflictsDatabase = conflictsDatabase();
        final ObjectDatabase objectDatabase = objectDatabase();
        final WorkingTree workingTree = workingTree();
        final RevTree currentWorkHead = workingTree.getTree();

        RevTree checkOutFromTree;

        final Set<String> unmerged = conflictsDatabase.findConflicts(null, paths);
        if (!unmerged.isEmpty()) {
            if (!(force || ours || theirs)) {
                StringBuilder msg = new StringBuilder();
                for (String path : unmerged) {
                    msg.append("error: path " + path + " is unmerged.\n");
                }
                throw new CheckoutException(msg.toString(), StatusCode.UNMERGED_PATHS);
            }
        }

        if (branchOrCommit != null) {
            Optional<ObjectId> id = command(ResolveTreeish.class).setTreeish(branchOrCommit).call();
            checkState(id.isPresent(), "'" + branchOrCommit + "' not found in repository.");
            checkOutFromTree = objectDatabase.getTree(id.get());
        } else {
            checkOutFromTree = stagingArea().getTree();
        }

        UpdateTree updateTree = command(UpdateTree.class).setRoot(currentWorkHead);

        Map<String, RevTreeBuilder> featureTypeTrees = new HashMap<>();
        Map<String, NodeRef> currentFeatureTypeRefs = new HashMap<>();

        for (String path : paths) {
            if (unmerged.contains(path)) {
                if (ours || theirs) {
                    String refspec = ours ? Ref.ORIG_HEAD : Ref.MERGE_HEAD;
                    ObjectId treeId = command(ResolveTreeish.class).setTreeish(refspec).call()
                            .orNull();
                    if (null != treeId) {
                        checkOutFromTree = objectDatabase.getTree(treeId);
                    }
                } else {// --force
                    continue;
                }
            }

            final NodeRef nodeRef = command(FindTreeChild.class).setParent(checkOutFromTree)
                    .setChildPath(path).call().orNull();

            if ((ours || theirs) && null == nodeRef) {
                // remove the node.
                command(RemoveOp.class).setRecursive(true).addPathToRemove(path).call();
                NodeRef foundChild = command(FindTreeChild.class).setParent(currentWorkHead)
                        .setChildPath(path).call().orNull();
                if (foundChild != null) {
                    if (TYPE.TREE.equals(foundChild.getType())) {
                        updateTree.removeChildTree(foundChild.path());
                    } else {
                        getTreeBuilder(currentWorkHead, foundChild, featureTypeTrees,
                                currentFeatureTypeRefs).remove(foundChild.getNode());
                    }
                }
            } else {
                checkArgument(null != nodeRef,
                        "pathspec '" + path + "' didn't match a feature in the tree");

                if (nodeRef.getType() == TYPE.TREE) {
                    updateTree.setChild(nodeRef);
                } else {
                    RevTreeBuilder treeBuilder = getTreeBuilder(currentWorkHead, nodeRef,
                            featureTypeTrees, currentFeatureTypeRefs);
                    treeBuilder.put(nodeRef.getNode());
                }
            }

        }

        for (Map.Entry<String, RevTreeBuilder> entry : featureTypeTrees.entrySet()) {
            final String changedTreePath = entry.getKey();
            final NodeRef currentTreeRef = currentFeatureTypeRefs.get(changedTreePath);
            checkState(null != currentTreeRef);
            final RevTreeBuilder changedTreeBuilder = entry.getValue();
            final RevTree changedTree = changedTreeBuilder.build();
            final Envelope newBounds = SpatialOps.boundsOf(changedTree);
            final NodeRef newTreeRef = currentTreeRef.update(changedTree.getId(), newBounds);
            updateTree.setChild(newTreeRef);
        }

        final RevTree newWorkHead = updateTree.call();
        workingTree.updateWorkHead(newWorkHead.getId());
        return checkoutResult;
    }

    private RevTreeBuilder getTreeBuilder(RevTree currentIndexHead, NodeRef featureRef,
            Map<String, RevTreeBuilder> featureTypeTrees,
            Map<String, NodeRef> currentFeatureTypeRefs) {

        checkArgument(TYPE.FEATURE.equals(featureRef.getType()));

        final String typeTreePath = featureRef.getParentPath();
        RevTreeBuilder typeTreeBuilder = featureTypeTrees.get(typeTreePath);
        if (typeTreeBuilder == null) {
            NodeRef typeTreeRef = context.command(FindTreeChild.class).setParent(currentIndexHead)
                    .setChildPath(typeTreePath).call().orNull();

            final RevTree currentTypeTree;
            if (typeTreeRef == null) {
                ObjectId metadataId = featureRef.getMetadataId();
                Node parentNode = RevObjectFactory.defaultInstance().createNode(
                        NodeRef.nodeFromPath(typeTreePath), EMPTY_TREE_ID, metadataId, TYPE.TREE,
                        null, null);
                typeTreeRef = NodeRef.create(NodeRef.parentPath(typeTreePath), parentNode);
                currentTypeTree = EMPTY;
            } else {
                currentTypeTree = context.objectDatabase().getTree(typeTreeRef.getObjectId());
            }
            typeTreeBuilder = RevTreeBuilder.builder(context.objectDatabase(),
                    currentTypeTree);
            currentFeatureTypeRefs.put(typeTreePath, typeTreeRef);
            featureTypeTrees.put(typeTreePath, typeTreeBuilder);
        }
        return typeTreeBuilder;
    }

    private CheckoutResult branchCheckout() throws CheckoutException {
        CheckoutResult checkoutResult = new CheckoutResult();
        final ConflictsDatabase conflictsDatabase = conflictsDatabase();
        final boolean hasConflicts = conflictsDatabase.hasConflicts(null);
        if (hasConflicts && !force) {
            throw buildConflictsException(conflictsDatabase);
        }
        Optional<Ref> targetRef = command(RefParse.class).setName(branchOrCommit).call();
        Optional<ObjectId> targetCommitId = Optional.absent();
        Optional<ObjectId> targetTreeId = Optional.absent();
        if (targetRef.isPresent()) {
            final java.util.Optional<String> remoteRepoName = Ref
                    .remoteName(targetRef.get().getName());

            final ObjectId commitId = targetRef.get().getObjectId();
            if (remoteRepoName.isPresent()) {
                String remoteName = remoteRepoName.get();
                if (branchOrCommit.contains(remoteName + '/')) {
                    RevCommit commit = command(RevObjectParse.class).setObjectId(commitId)
                            .call(RevCommit.class).get();

                    targetTreeId = Optional.of(commit.getTreeId());
                    targetCommitId = Optional.of(commit.getId());
                    targetRef = Optional.absent();
                } else {
                    // Remote remote = command(remotere);
                    // command(MapRef.class).setRemote(remote).add(targetRef.get()).call().get(0);

                    Ref branch = command(BranchCreateOp.class).setName(targetRef.get().localName())
                            .setSource(commitId.toString())//
                            .setRemoteName(remoteName)//
                            .setRemoteBranch(targetRef.get().localName()).call();

                    command(BranchConfigOp.class).setName(branch.localName())
                            .setRemoteName(remoteName).setRemoteBranch(targetRef.get().localName())
                            .set();

                    targetRef = Optional.of(branch);
                    checkoutResult.setResult(CheckoutResult.Results.CHECKOUT_REMOTE_BRANCH);
                    checkoutResult.setRemoteName(remoteName);
                }
            }

            if (commitId.isNull()) {
                targetTreeId = Optional.of(ObjectId.NULL);
                targetCommitId = Optional.of(ObjectId.NULL);
            } else {
                Optional<RevCommit> parsed = command(RevObjectParse.class).setObjectId(commitId)
                        .call(RevCommit.class);
                checkState(parsed.isPresent());
                checkState(parsed.get() instanceof RevCommit);
                RevCommit commit = parsed.get();
                targetCommitId = Optional.of(commit.getId());
                targetTreeId = Optional.of(commit.getTreeId());
            }
        } else {
            final Optional<ObjectId> addressed = command(RevParse.class).setRefSpec(branchOrCommit)
                    .call();
            checkArgument(addressed.isPresent(),
                    "source '" + branchOrCommit + "' not found in repository");

            RevCommit commit = command(RevObjectParse.class).setObjectId(addressed.get())
                    .call(RevCommit.class).get();

            targetTreeId = Optional.of(commit.getTreeId());
            targetCommitId = Optional.of(commit.getId());
        }
        if (targetTreeId.isPresent()) {
            if (!force) {
                if (!stagingArea().isClean() || !workingTree().isClean()) {
                    throw new CheckoutException(StatusCode.LOCAL_CHANGES_NOT_COMMITTED);
                }
            }
            // update work tree
            ObjectId treeId = targetTreeId.get();
            workingTree().updateWorkHead(treeId);
            stagingArea().updateStageHead(treeId);
            checkoutResult.setNewTree(treeId);
            if (targetRef.isPresent()) {
                // update HEAD
                Ref target = targetRef.get();
                // beware of cyclic refs, peel symrefs
                String refName = target.peel().getName();
                command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(refName).call();
                checkoutResult.setNewRef(targetRef.get());
                checkoutResult.setOid(targetCommitId.get());
                checkoutResult.setResult(CheckoutResult.Results.CHECKOUT_LOCAL_BRANCH);
            } else {
                // set HEAD to a dettached state
                ObjectId commitId = targetCommitId.get();
                command(UpdateRef.class).setName(Ref.HEAD).setNewValue(commitId).call();
                checkoutResult.setOid(commitId);
                checkoutResult.setResult(CheckoutResult.Results.DETACHED_HEAD);
            }
            Optional<Ref> ref = command(RefParse.class).setName(Ref.MERGE_HEAD).call();
            if (ref.isPresent()) {
                command(UpdateRef.class).setName(Ref.MERGE_HEAD).setDelete(true).call();
            }
        }
        return checkoutResult;
    }

    private CheckoutException buildConflictsException(final ConflictsDatabase conflictsDatabase) {
        final long conflictCount = conflictsDatabase.getCountByPrefix(null, null);
        Iterator<Conflict> conflicts = Iterators
                .limit(conflictsDatabase.getByPrefix(branchOrCommit, null), 25);
        StringBuilder msg = new StringBuilder();
        while (conflicts.hasNext()) {
            Conflict conflict = conflicts.next();
            msg.append("error: " + conflict.getPath() + " needs merge.\n");
        }
        if (conflictCount > 25) {
            msg.append(String.format("and %,d more.\n", (conflictCount - 25)));
        }
        msg.append("You need to resolve your index first.\n");
        return new CheckoutException(msg.toString(), StatusCode.UNMERGED_PATHS);
    }
}
