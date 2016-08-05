/* Copyright (c) 2012-2014 Boundless and others.
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

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.RevTreeBuilder;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.plumbing.WriteBack;
import org.locationtech.geogig.porcelain.CheckoutException.StatusCode;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigAction;
import org.locationtech.geogig.porcelain.ConfigOp.ConfigScope;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.storage.ConflictsDatabase;

import com.google.common.base.Optional;
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
    protected CheckoutResult _call() {
        checkState(branchOrCommit != null || !paths.isEmpty(),
                "No branch, tree, or path were specified");
        checkArgument(!(ours && theirs), "Cannot use both --ours and --theirs.");
        checkArgument((ours == theirs) || branchOrCommit == null,
                "--ours/--theirs is incompatible with switching branches.");

        CheckoutResult result = new CheckoutResult();

        final ConflictsDatabase conflictsDatabase = conflictsDatabase();
        final boolean hasConflicts = conflictsDatabase.hasConflicts(null);
        if (!paths.isEmpty()) {
            result.setResult(CheckoutResult.Results.UPDATE_OBJECTS);
            Optional<RevTree> tree = Optional.absent();
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
                Optional<ObjectId> id = command(ResolveTreeish.class).setTreeish(branchOrCommit)
                        .call();
                checkState(id.isPresent(), "'" + branchOrCommit + "' not found in repository.");
                tree = command(RevObjectParse.class).setObjectId(id.get()).call(RevTree.class);

            } else {
                tree = Optional.of(index().getTree());
            }

            Optional<RevTree> mainTree = tree;

            for (String st : paths) {
                if (unmerged.contains(st)) {
                    if (ours || theirs) {
                        String refspec = ours ? Ref.ORIG_HEAD : Ref.MERGE_HEAD;
                        Optional<ObjectId> treeId = command(ResolveTreeish.class)
                                .setTreeish(refspec).call();
                        if (treeId.isPresent()) {
                            tree = command(RevObjectParse.class).setObjectId(treeId.get())
                                    .call(RevTree.class);
                        }
                    } else {// --force
                        continue;
                    }
                } else {
                    tree = mainTree;
                }

                Optional<NodeRef> node = command(FindTreeChild.class).setParent(tree.get())
                        .setChildPath(st).call();

                if ((ours || theirs) && !node.isPresent()) {
                    // remove the node.
                    command(RemoveOp.class).setRecursive(true).addPathToRemove(st).call();
                } else {
                    checkArgument(node.isPresent(),
                            "pathspec '" + st + "' didn't match a feature in the tree");

                    if (node.get().getType() == TYPE.TREE) {
                        RevTree newRoot;
                        RevTreeBuilder treeBuilder = RevTreeBuilder.canonical(objectDatabase(),
                                workingTree().getTree());
                        treeBuilder.remove(st);
                        treeBuilder.put(node.get().getNode());
                        newRoot = treeBuilder.build();

                        workingTree().updateWorkHead(newRoot.getId());
                    } else {

                        ObjectId metadataId = ObjectId.NULL;
                        Optional<NodeRef> parentNode = command(FindTreeChild.class)
                                .setParent(workingTree().getTree())
                                .setChildPath(node.get().getParentPath()).call();
                        RevTreeBuilder treeBuilder = null;
                        if (parentNode.isPresent()) {
                            metadataId = parentNode.get().getMetadataId();
                            Optional<RevTree> parsed = command(RevObjectParse.class)
                                    .setObjectId(parentNode.get().getNode().getObjectId())
                                    .call(RevTree.class);
                            checkArgument(parsed.isPresent(),
                                    "Parent tree couldn't be found in the repository.");
                            treeBuilder = RevTreeBuilder.canonical(objectDatabase(), parsed.get());
                            treeBuilder.remove(node.get().getNode().getName());
                        } else {
                            treeBuilder = RevTreeBuilder.canonical(objectDatabase());
                        }
                        treeBuilder.put(node.get().getNode());

                        RevTree newTree = treeBuilder.build();

                        ObjectId newTreeId = command(WriteBack.class)
                                .setAncestor(workingTree().getTree())
                                .setChildPath(node.get().getParentPath()).setTree(newTree)
                                .setMetadataId(metadataId).call();
                        workingTree().updateWorkHead(newTreeId);
                    }
                }
            }

        } else {
            if (hasConflicts) {
                if (!(force)) {
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
                    throw new CheckoutException(msg.toString(), StatusCode.UNMERGED_PATHS);
                }
            }
            Optional<Ref> targetRef = Optional.absent();
            Optional<ObjectId> targetCommitId = Optional.absent();
            Optional<ObjectId> targetTreeId = Optional.absent();
            targetRef = command(RefParse.class).setName(branchOrCommit).call();
            if (targetRef.isPresent()) {
                ObjectId commitId = targetRef.get().getObjectId();
                if (targetRef.get().getName().startsWith(Ref.REMOTES_PREFIX)) {
                    String remoteName = targetRef.get().getName();
                    remoteName = remoteName.substring(Ref.REMOTES_PREFIX.length(),
                            targetRef.get().getName().lastIndexOf("/"));

                    if (branchOrCommit.contains(remoteName + '/')) {
                        RevCommit commit = command(RevObjectParse.class).setObjectId(commitId)
                                .call(RevCommit.class).get();

                        targetTreeId = Optional.of(commit.getTreeId());
                        targetCommitId = Optional.of(commit.getId());
                        targetRef = Optional.absent();
                    } else {

                        Ref branch = command(BranchCreateOp.class)
                                .setName(targetRef.get().localName()).setSource(commitId.toString())
                                .call();

                        command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET)
                                .setScope(ConfigScope.LOCAL)
                                .setName("branches." + branch.localName() + ".remote")
                                .setValue(remoteName).call();

                        command(ConfigOp.class).setAction(ConfigAction.CONFIG_SET)
                                .setScope(ConfigScope.LOCAL)
                                .setName("branches." + branch.localName() + ".merge")
                                .setValue(targetRef.get().getName()).call();

                        targetRef = Optional.of(branch);
                        result.setResult(CheckoutResult.Results.CHECKOUT_REMOTE_BRANCH);
                        result.setRemoteName(remoteName);
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
                final Optional<ObjectId> addressed = command(RevParse.class)
                        .setRefSpec(branchOrCommit).call();
                checkArgument(addressed.isPresent(),
                        "source '" + branchOrCommit + "' not found in repository");

                RevCommit commit = command(RevObjectParse.class).setObjectId(addressed.get())
                        .call(RevCommit.class).get();

                targetTreeId = Optional.of(commit.getTreeId());
                targetCommitId = Optional.of(commit.getId());
            }
            if (targetTreeId.isPresent()) {
                if (!force) {
                    if (!index().isClean() || !workingTree().isClean()) {
                        throw new CheckoutException(StatusCode.LOCAL_CHANGES_NOT_COMMITTED);
                    }
                }
                // update work tree
                ObjectId treeId = targetTreeId.get();
                workingTree().updateWorkHead(treeId);
                index().updateStageHead(treeId);
                result.setNewTree(treeId);
                if (targetRef.isPresent()) {
                    // update HEAD
                    Ref target = targetRef.get();
                    String refName;
                    if (target instanceof SymRef) {// beware of cyclic refs, peel symrefs
                        refName = ((SymRef) target).getTarget();
                    } else {
                        refName = target.getName();
                    }
                    command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(refName).call();
                    result.setNewRef(targetRef.get());
                    result.setOid(targetCommitId.get());
                    result.setResult(CheckoutResult.Results.CHECKOUT_LOCAL_BRANCH);
                } else {
                    // set HEAD to a dettached state
                    ObjectId commitId = targetCommitId.get();
                    command(UpdateRef.class).setName(Ref.HEAD).setNewValue(commitId).call();
                    result.setOid(commitId);
                    result.setResult(CheckoutResult.Results.DETACHED_HEAD);
                }
                Optional<Ref> ref = command(RefParse.class).setName(Ref.MERGE_HEAD).call();
                if (ref.isPresent()) {
                    command(UpdateRef.class).setName(Ref.MERGE_HEAD).setDelete(true).call();
                }

                return result;
            }

        }
        result.setNewTree(workingTree().getTree().getId());
        return result;
    }
}
