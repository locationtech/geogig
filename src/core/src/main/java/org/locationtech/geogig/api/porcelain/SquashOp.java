/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api.porcelain;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.CommitBuilder;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.SymRef;
import org.locationtech.geogig.api.plumbing.FindCommonAncestor;
import org.locationtech.geogig.api.plumbing.ForEachRef;
import org.locationtech.geogig.api.plumbing.RefParse;
import org.locationtech.geogig.api.plumbing.UpdateRef;
import org.locationtech.geogig.api.plumbing.UpdateSymRef;
import org.locationtech.geogig.api.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.GraphDatabase;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Suppliers;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Operation to squash commits into one.
 */
public class SquashOp extends AbstractGeoGigOp<ObjectId> {

    private RevCommit since;

    private RevCommit until;

    private String message;

    /**
     * Indicates the first commit to squash. If no message is provided, the message from this commit
     * will be used
     * 
     * @param the first (oldest) commit to squash
     * @return {@code this}
     */
    public SquashOp setSince(final RevCommit since) {
        this.since = since;
        return this;
    }

    /**
     * Indicates the last commit to squash
     * 
     * @param the last (most recent) commit to squash
     * @return {@code this}
     */
    public SquashOp setUntil(RevCommit until) {
        this.until = until;
        return this;
    }

    /**
     * Indicates the message to use for the commit. If null, the message from the 'since' commit
     * will be used
     * 
     * @param the message to use for the commit
     * @return {@code this}
     */
    public SquashOp setMessage(String message) {
        this.message = message;
        return this;
    }

    /**
     * Executes the squash operation.
     * 
     * @return the new head after modifying the history squashing commits
     * @see org.locationtech.geogig.api.AbstractGeoGigOp#call()
     */
    @Override
    protected ObjectId _call() {

        Preconditions.checkNotNull(since);
        Preconditions.checkNotNull(until);

        GraphDatabase graphDb = graphDatabase();
        Repository repository = repository();
        Platform platform = platform();

        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        Preconditions.checkState(currHead.isPresent(), "Repository has no HEAD, can't squash.");
        Preconditions.checkState(currHead.get() instanceof SymRef,
                "Can't squash from detached HEAD");
        final SymRef headRef = (SymRef) currHead.get();
        final String currentBranch = headRef.getTarget();

        Preconditions.checkState(index().isClean() && workingTree().isClean(),
                "You must have a clean working tree and index to perform a squash.");

        Optional<ObjectId> ancestor = command(FindCommonAncestor.class).setLeft(since)
                .setRight(until).call();
        Preconditions.checkArgument(ancestor.isPresent(),
                "'since' and 'until' command do not have a common ancestor");
        Preconditions.checkArgument(ancestor.get().equals(since.getId()),
                "Commits provided in wrong order");

        Preconditions.checkArgument(!since.getParentIds().isEmpty(),
                "'since' commit has no parents");

        // we get a a list of commits to apply on top of the squashed commits
        List<RevCommit> commits = getCommitsAfterUntil();

        ImmutableSet<Ref> refs = command(ForEachRef.class).setPrefixFilter("refs/heads").call();

        // we create a list of all parents of those squashed commits, in case they are
        // merge commits. The resulting commit will have all these parents
        //
        // While iterating the set of commits to squash, we check that there are no branch starting
        // points among them. Any commit with more than one child causes an exception to be thrown,
        // since the squash operation does not support squashing those commits

        Iterator<RevCommit> toSquash = command(LogOp.class).setSince(since.getParentIds().get(0))
                .setUntil(until.getId()).setFirstParentOnly(true).call();
        List<ObjectId> firstParents = Lists.newArrayList();
        List<ObjectId> secondaryParents = Lists.newArrayList();
        final List<ObjectId> squashedIds = Lists.newArrayList();
        RevCommit commitToSquash = until;
        while (toSquash.hasNext()) {
            commitToSquash = toSquash.next();
            squashedIds.add(commitToSquash.getId());
            Preconditions
                    .checkArgument(
                            graphDb.getChildren(commitToSquash.getId()).size() < 2,
                            "The commits to squash include a branch starting point. Squashing that type of commit is not supported.");
            for (Ref ref : refs) {
                // In case a branch has been created but no commit has been made on it and the
                // starting commit has just one child
                Preconditions
                        .checkArgument(
                                !ref.getObjectId().equals(commitToSquash.getId())
                                        || ref.getObjectId().equals(currHead.get().getObjectId())
                                        || commitToSquash.getParentIds().size() > 1,
                                "The commits to squash include a branch starting point. Squashing that type of commit is not supported.");
            }
            ImmutableList<ObjectId> parentIds = commitToSquash.getParentIds();
            for (int i = 1; i < parentIds.size(); i++) {
                secondaryParents.add(parentIds.get(i));
            }
            firstParents.add(parentIds.get(0));
        }
        Preconditions.checkArgument(since.equals(commitToSquash),
                "Cannot reach 'since' from 'until' commit through first parentage");

        // We do the same check in the children commits
        for (RevCommit commit : commits) {
            Preconditions
                    .checkArgument(
                            graphDb.getChildren(commit.getId()).size() < 2,
                            "The commits after the ones to squash include a branch starting point. This scenario is not supported.");
            for (Ref ref : refs) {
                // In case a branch has been created but no commit has been made on it
                Preconditions
                        .checkArgument(
                                !ref.getObjectId().equals(commit.getId())
                                        || ref.getObjectId().equals(currHead.get().getObjectId())
                                        || commit.getParentIds().size() > 1,
                                "The commits after the ones to squash include a branch starting point. This scenario is not supported.");
            }
        }

        ObjectId newHead;
        // rewind the head
        newHead = since.getParentIds().get(0);
        command(ResetOp.class).setCommit(Suppliers.ofInstance(newHead)).setMode(ResetMode.HARD)
                .call();

        // add the current HEAD as first parent of the resulting commit
        // parents.add(0, newHead);

        // Create new commit
        List<ObjectId> parents = Lists.newArrayList();
        parents.addAll(firstParents);
        parents.addAll(secondaryParents);
        ObjectId endTree = until.getTreeId();
        CommitBuilder builder = new CommitBuilder(until);
        Collection<ObjectId> filteredParents = Collections2.filter(parents,
                new Predicate<ObjectId>() {
                    @Override
                    public boolean apply(@Nullable ObjectId id) {
                        return !squashedIds.contains(id);
                    }

                });

        builder.setParentIds(Lists.newArrayList(filteredParents));
        builder.setTreeId(endTree);
        if (message == null) {
            message = since.getMessage();
        }
        long timestamp = platform.currentTimeMillis();
        builder.setMessage(message);
        builder.setCommitter(resolveCommitter());
        builder.setCommitterEmail(resolveCommitterEmail());
        builder.setCommitterTimestamp(timestamp);
        builder.setCommitterTimeZoneOffset(platform.timeZoneOffset(timestamp));
        builder.setAuthorTimestamp(until.getAuthor().getTimestamp());

        RevCommit newCommit = builder.build();
        repository.objectDatabase().put(newCommit);

        newHead = newCommit.getId();
        ObjectId newTreeId = newCommit.getTreeId();

        command(UpdateRef.class).setName(currentBranch).setNewValue(newHead).call();
        command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(currentBranch).call();

        workingTree().updateWorkHead(newTreeId);
        index().updateStageHead(newTreeId);

        // now put the other commits after the squashed one
        newHead = addCommits(commits, currentBranch, newHead);

        return newHead;

    }

    private ObjectId addCommits(List<RevCommit> commits, String currentBranch,
            final ObjectId squashedId) {

        final Platform platform = platform();
        final Map<ObjectId, ObjectId> replacedCommits = Maps.newHashMap();
        replacedCommits.put(until.getId(), squashedId);
        ObjectId head = squashedId;
        for (RevCommit commit : commits) {
            CommitBuilder builder = new CommitBuilder(commit);
            Collection<ObjectId> parents = Collections2.transform(commit.getParentIds(),
                    new Function<ObjectId, ObjectId>() {
                        @Override
                        @Nullable
                        public ObjectId apply(@Nullable ObjectId id) {
                            if (replacedCommits.containsKey(id)) {
                                return replacedCommits.get(id);
                            } else {
                                return id;
                            }
                        }
                    });
            builder.setParentIds(Lists.newArrayList(parents));
            builder.setTreeId(commit.getTreeId());
            long timestamp = platform.currentTimeMillis();
            builder.setCommitterTimestamp(timestamp);
            builder.setCommitterTimeZoneOffset(platform.timeZoneOffset(timestamp));

            RevCommit newCommit = builder.build();
            replacedCommits.put(commit.getId(), newCommit.getId());
            objectDatabase().put(newCommit);
            head = newCommit.getId();
            ObjectId newTreeId = newCommit.getTreeId();

            command(UpdateRef.class).setName(currentBranch).setNewValue(head).call();
            command(UpdateSymRef.class).setName(Ref.HEAD).setNewValue(currentBranch).call();

            workingTree().updateWorkHead(newTreeId);
            index().updateStageHead(newTreeId);
        }

        return head;
    }

    private List<RevCommit> getCommitsAfterUntil() {
        Iterator<RevCommit> commitIterator = command(LogOp.class).setSince(until.getId()).call();
        List<RevCommit> commits = Lists.newArrayList(commitIterator);
        Collections.reverse(commits);
        return commits;
    }

    private String resolveCommitter() {
        final String key = "user.name";
        Optional<String> name = command(ConfigGet.class).setName(key).call();

        checkState(
                name.isPresent(),
                "%s not found in config. Use geogig config [--global] %s <your name> to configure it.",
                key, key);

        return name.get();
    }

    private String resolveCommitterEmail() {
        final String key = "user.email";
        Optional<String> email = command(ConfigGet.class).setName(key).call();

        checkState(
                email.isPresent(),
                "%s not found in config. Use geogig config [--global] %s <your email> to configure it.",
                key, key);

        return email.get();
    }

}
