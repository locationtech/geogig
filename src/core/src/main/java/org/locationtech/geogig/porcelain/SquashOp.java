/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static org.locationtech.geogig.base.Preconditions.checkState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevCommitBuilder;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.ForEachRef;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.GraphDatabase;

import com.google.common.collect.Streams;

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
     * @see org.locationtech.geogig.repository.impl.AbstractGeoGigOp#call()
     */
    protected @Override ObjectId _call() {

        Objects.requireNonNull(since);
        Objects.requireNonNull(until);

        GraphDatabase graphDb = graphDatabase();
        Repository repository = repository();
        Platform platform = platform();

        final Optional<Ref> currHead = command(RefParse.class).setName(Ref.HEAD).call();
        Preconditions.checkState(currHead.isPresent(), "Repository has no HEAD, can't squash.");
        Preconditions.checkState(currHead.get() instanceof SymRef,
                "Can't squash from detached HEAD");
        final SymRef headRef = (SymRef) currHead.get();
        final String currentBranch = headRef.getTarget();

        Preconditions.checkState(stagingArea().isClean() && workingTree().isClean(),
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

        Set<Ref> refs = command(ForEachRef.class).setPrefixFilter(Ref.HEADS_PREFIX).call();

        // we create a list of all parents of those squashed commits, in case they are
        // merge commits. The resulting commit will have all these parents
        //
        // While iterating the set of commits to squash, we check that there are no branch starting
        // points among them. Any commit with more than one child causes an exception to be thrown,
        // since the squash operation does not support squashing those commits

        Iterator<RevCommit> toSquash = command(LogOp.class).setSince(since.getParentIds().get(0))
                .setUntil(until.getId()).setFirstParentOnly(true).call();
        List<ObjectId> firstParents = new ArrayList<>();
        List<ObjectId> secondaryParents = new ArrayList<>();
        final List<ObjectId> squashedIds = new ArrayList<>();
        RevCommit commitToSquash = until;
        while (toSquash.hasNext()) {
            commitToSquash = toSquash.next();
            squashedIds.add(commitToSquash.getId());
            Preconditions.checkArgument(graphDb.getChildren(commitToSquash.getId()).size() < 2,
                    "The commits to squash include a branch starting point. Squashing that type of commit is not supported.");
            for (Ref ref : refs) {
                // In case a branch has been created but no commit has been made on it and the
                // starting commit has just one child
                Preconditions.checkArgument(
                        !ref.getObjectId().equals(commitToSquash.getId())
                                || ref.getObjectId().equals(currHead.get().getObjectId())
                                || commitToSquash.getParentIds().size() > 1,
                        "The commits to squash include a branch starting point. Squashing that type of commit is not supported.");
            }
            List<ObjectId> parentIds = commitToSquash.getParentIds();
            for (int i = 1; i < parentIds.size(); i++) {
                secondaryParents.add(parentIds.get(i));
            }
            firstParents.add(parentIds.get(0));
        }
        Preconditions.checkArgument(since.equals(commitToSquash),
                "Cannot reach 'since' from 'until' commit through first parentage");

        // We do the same check in the children commits
        for (RevCommit commit : commits) {
            Preconditions.checkArgument(graphDb.getChildren(commit.getId()).size() < 2,
                    "The commits after the ones to squash include a branch starting point. This scenario is not supported.");
            for (Ref ref : refs) {
                // In case a branch has been created but no commit has been made on it
                Preconditions.checkArgument(
                        !ref.getObjectId().equals(commit.getId())
                                || ref.getObjectId().equals(currHead.get().getObjectId())
                                || commit.getParentIds().size() > 1,
                        "The commits after the ones to squash include a branch starting point. This scenario is not supported.");
            }
        }

        // rewind the head
        ObjectId newHead = since.getParentIds().get(0);
        command(ResetOp.class).setCommit(newHead).setMode(ResetMode.HARD).call();

        // add the current HEAD as first parent of the resulting commit
        // parents.add(0, newHead);

        // Create new commit
        List<ObjectId> parents = new ArrayList<>();
        parents.addAll(firstParents);
        parents.addAll(secondaryParents);
        ObjectId endTree = until.getTreeId();
        RevCommitBuilder builder = RevCommit.builder().platform(this.platform()).init(until);

        List<ObjectId> filteredParents = parents.stream().filter(id -> !squashedIds.contains(id))
                .collect(Collectors.toList());
        builder.parentIds(filteredParents);
        builder.treeId(endTree);
        if (message == null) {
            message = since.getMessage();
        }
        long timestamp = platform.currentTimeMillis();
        builder.message(message);
        builder.committer(resolveCommitter());
        builder.committerEmail(resolveCommitterEmail());
        builder.committerTimestamp(timestamp);
        builder.committerTimeZoneOffset(platform.timeZoneOffset(timestamp));
        builder.authorTimestamp(until.getAuthor().getTimestamp());

        RevCommit newCommit = builder.build();
        repository.context().objectDatabase().put(newCommit);

        newHead = newCommit.getId();
        ObjectId newTreeId = newCommit.getTreeId();

        String reason;
        if (Objects.equals(since, until)) {
            reason = String.format("squash %s '%s'", until.getId(),
                    RevObjects.messageTitle(newCommit.getMessage()));
        } else {
            reason = String.format("squash from %s to %s '%s'", since.getId(), until.getId(),
                    RevObjects.messageTitle(newCommit.getMessage()));
        }
        final Ref updatedRef = new Ref(currentBranch, newHead);
        command(UpdateRefs.class).setReason(reason)//
                .add(updatedRef)//
                .add(new SymRef(Ref.HEAD, updatedRef))//
                .add(Ref.WORK_HEAD, newTreeId)//
                .add(Ref.STAGE_HEAD, newTreeId)//
                .call();

        // now put the other commits after the squashed one
        newHead = addCommits(commits, currentBranch, newHead);

        return newHead;

    }

    private ObjectId addCommits(List<RevCommit> commits, String branchName,
            final ObjectId squashedId) {

        final Platform platform = platform();
        final Map<ObjectId, ObjectId> replacedCommits = new HashMap<>();
        replacedCommits.put(until.getId(), squashedId);
        ObjectId head = squashedId;
        for (RevCommit commit : commits) {
            RevCommitBuilder builder = RevCommit.builder().platform(this.platform()).init(commit);

            List<ObjectId> parents = commit.getParentIds().stream()
                    .map(id -> replacedCommits.getOrDefault(id, id)).collect(Collectors.toList());
            builder.parentIds(parents);
            builder.treeId(commit.getTreeId());
            long timestamp = platform.currentTimeMillis();
            builder.committerTimestamp(timestamp);
            builder.committerTimeZoneOffset(platform.timeZoneOffset(timestamp));

            RevCommit newCommit = builder.build();
            replacedCommits.put(commit.getId(), newCommit.getId());
            objectDatabase().put(newCommit);
            head = newCommit.getId();
            ObjectId newTreeId = newCommit.getTreeId();
            final Ref updatedBranch = new Ref(branchName, head);
            command(UpdateRefs.class)//
                    .setReason("squash: " + commit.getId())//
                    .add(updatedBranch)//
                    .add(new SymRef(Ref.HEAD, updatedBranch))//
                    .add(Ref.WORK_HEAD, newTreeId)//
                    .add(Ref.STAGE_HEAD, newTreeId)//
                    .call();
        }

        return head;
    }

    private List<RevCommit> getCommitsAfterUntil() {
        Iterator<RevCommit> commitIterator = command(LogOp.class).setSince(until.getId()).call();
        List<RevCommit> commits = Streams.stream(commitIterator).collect(Collectors.toList());
        Collections.reverse(commits);
        return commits;
    }

    private String resolveCommitter() {
        final String namekey = "user.name";

        String name = getClientData(namekey)
                .orElseGet(() -> command(ConfigGet.class).setName(namekey).call().orElse(null));

        checkState(name != null,
                "%s not found in config. Use geogig config [--global] %s <your name> to configure it.",
                namekey, namekey);

        return name;
    }

    private String resolveCommitterEmail() {
        final String emailkey = "user.email";

        String email = getClientData(emailkey)
                .orElseGet(() -> command(ConfigGet.class).setName(emailkey).call().orElse(null));

        checkState(email != null,
                "%s not found in config. Use geogig config [--global] %s <your email> to configure it.",
                emailkey, emailkey);

        return email;
    }

}
