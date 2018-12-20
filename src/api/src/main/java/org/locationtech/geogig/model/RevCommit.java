/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * A revision commit is an immutable data structure that represents the full state of the repository
 * at a given point in time (by pointing to a root {@link RevTree tree}), and contains information
 * about who and when created the snapshot, as well as what commit or commits it was created from
 * (it's parent commit(s)), and an author provided message explaining the rationale for the
 * snapshot.
 * <p>
 * A commit is a root entry point to the revision graph ({@code commit [1]-> tree [0..N]-> tree 
 * <FeatureType> [0..N]-> feature}).
 * <p>
 * A commit by itself does not represent a set of changes to the repository. By pointing to a root
 * {@link RevTree tree}, it represents a whole dataset (i.e. a full snapshot of the repository at a
 * given point in time).
 * <p>
 * Since a commit also has pointer(s) to it's parent(s) commit(s) ({@link #getParentIds()}), the set
 * of changes between a given commit and any of its parent commits is to be computed by computing
 * the differences between the tree the commit points to and one of its parents'. In fact, the
 * differences between one commit an any other can be computed just as easily.
 * <p>
 * A {@code RevCommit} contains information about the {@link #getAuthor() author} and
 * {@link #getCommitter() committer}.
 * <p>
 * The <b>author</b> is the individual that created the repository snapshot (i.e. the root tree a
 * commit points to). What exactly the author contributed is to be determined by computing the
 * differences between the snapshot and the commit's parents.
 * <p>
 * The <b>committer</b> is the individual that created the commit object itself, as opposed to the
 * data it points to.
 * <p>
 * Author and committer are most of the time the same, but in some cases they may not be. Sometimes,
 * the commit history may be re-written, for example to fix a typo in a commit message, to replay
 * the changes a commit represents with regard to its parent on top of another commit (so-called
 * "rebasing"), or to apply a patch given as a patch file with contents authored by someone else.
 * Since commits are immutable data structures, in such cases a new commit must be created, and the
 * resulting commit will still credit the original author, but record the individual that created
 * the new commit as the committer.
 * 
 * @since 1.0
 */
public interface RevCommit extends RevObject {

    /**
     * The id of the root {@link RevTree tree} this commit points to.
     * 
     * @return the id of the tree this commit points to
     */
    public ObjectId getTreeId();

    /**
     * The list of parent commits of this commit.
     * <p>
     * If the list of parent commit ids is empty, it means the commit is the first one created in an
     * orphan branch (like the first commit at all created in a given repository, or the first
     * created in a branch that wasn't forked from another branch - hence the name "orphan branch
     * -)."
     * <p>
     * A commit with one single parent identifier is a snapshot that was created on top of another
     * one.
     * <p>
     * A commit with more than one parent is called a "merge commit", as the root tree it points to
     * is the result of merging together the contents of the root trees the parent commits point to.
     * <p>
     * The process of merging the parents' root trees to create the merge commit's root tree may
     * fail due to conflicts, for example, if the two parents modified the same attribute of the
     * same feature with a different value, relative to the commits' common ancestor. In that case,
     * the conflict needs to be resolved before creating the final root tree.
     * <p>
     * A commit with more than one parent identifier is the result of merging two or more branches
     * together, so that each parent id points to the tip of one of the merged branches (note the
     * mention of merging branches is because that's the most common use case, but it doesn't really
     * need to be the tip of an actual branch, the merged commits may have been selected
     * individually regardless of being the tip of a branch or not).
     * 
     * @implNote currently GeoGig does not support creating an octopus merge if there are merge
     *           conflicts between any of the commits to be merged.
     * 
     * @return the list of parent commit ids for this commit
     */
    public ImmutableList<ObjectId> getParentIds();

    /**
     * Short cut for {@code getParentIds().get(parentIndex)}, retuning an optional with the parent
     * id at {@code parentIndex} or {@link Optional#absent() absent} if {@code parentIndex} is out
     * of bounds.
     * <p>
     * Beware {@code parentIndex} is <b>zero-based</b>, whilst the command line interface syntax for
     * parents is one-based (e.g. {@code <commit id>^1} for the first parent instead of
     * {@code <commit id>^0}).
     * 
     * @TODO: rephrase the above paragraph to refer to the rev-parse format instead of the command
     *        line interface once documented properly
     * 
     * @param parentIndex zero-based index in the {@link #getParentIds() list of parent ids} to
     *        return
     * @return the parent id at the given index, or {@link Optional#absent() absent} if
     *         {@code parentIndex} is out of bounds
     */
    public Optional<ObjectId> parentN(int parentIndex);

    /**
     * The {@link RevPerson} object that represents the individual that originally authored the data
     * snapshot this commit represents and the timestamp when the snapshot was created.
     * <p>
     * See the class' javadocs for an explanation of the difference between author and committer.
     * 
     * @return the person object representing the original individual and creation time of the
     *         snapshot
     */
    public RevPerson getAuthor();

    /**
     * The {@link RevPerson} object that represents the individual that finally created the commit
     * object itself (as opposed to the dataset snapshot it points to) and the time the commit was
     * created.
     * <p>
     * See the class' javadocs for an explanation of the difference between author and committer.
     * 
     * @return the person object representing the individual and time when the snapshot was actually
     *         committed
     */
    public RevPerson getCommitter();

    /**
     * A human readable explanation for the rationale of creating the snapshot dataset, provided by
     * the commit author, or the committer if it amended the commit message.
     * 
     * @return the message provided by the author as rationale to create the snapshot this commit
     *         represents
     */
    public String getMessage();

    public static RevCommitBuilder builder() {
        return new RevCommitBuilder();
    }
}