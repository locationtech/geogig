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

import static org.locationtech.geogig.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.locationtech.geogig.di.CanRunDuringConflict;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.GraphDatabase;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.Range;
import com.google.common.collect.Streams;

import lombok.NonNull;

/**
 * Operation to query the commits logs.
 * <p>
 * The list of commits to return can be filtered by setting the following properties:
 * <ul>
 * <li>{@link #setLimit(int) limit}: Limits the number of commits to return.
 * <li>{@link #setTimeRange(Range) timeRange}: return commits that fall in to the given time range.
 * <li>{@link #setSince(ObjectId) since}...{@link #setUntil(ObjectId) until}: Show only commits
 * between the named two commits.
 * <li>{@link #addPath(String) addPath}: Show only commits that affect the specified path.
 * </ul>
 * </p>
 * 
 * 
 */
@CanRunDuringConflict
public class LogOp extends AbstractGeoGigOp<Iterator<RevCommit>> {

    private static final Range<Date> ALWAYS = Range.all();

    private Range<Date> timeRange;

    private Integer skip;

    private Integer limit;

    private ObjectId since;

    private ObjectId until;

    private Set<String> paths;

    private Pattern author;

    private Pattern commiter;

    private boolean topo;

    private boolean firstParent;

    private List<ObjectId> commits = new ArrayList<>();

    public LogOp() {
        timeRange = ALWAYS;
    }

    /**
     * @param skip sets the number of commits to skip from the commit list
     * @return {@code this}
     */
    public LogOp setSkip(int skip) {
        this.skip = Integer.valueOf(skip);
        return this;
    }

    /**
     * @param limit sets the limit for the amount of commits to show
     * @return {@code this}
     */
    public LogOp setLimit(int limit) {
        this.limit = Integer.valueOf(limit);
        return this;
    }

    /**
     * Indicates to return only commits newer than the given one ({@code since} is exclusive)
     * 
     * @param since the initial (oldest and exclusive) commit id, ({@code null} sets the default)
     * @return {@code this}
     * @see #setUntil(ObjectId)
     */
    public LogOp setSince(final ObjectId since) {
        this.since = since;
        return this;
    }

    /**
     * Indicates to return commits up to the provided one, inclusive.
     * 
     * @param until the final (newest and inclusive) commit id, ({@code null} sets the default)
     * @return {@code this}
     * @see #setSince(ObjectId)
     */
    public LogOp setUntil(ObjectId until) {
        this.until = until;
        return this;
    }

    /**
     * Sets whether commits should be ordered not according to its date, but to is structure in the
     * history branches
     * 
     * @param topo true if commits should be ordered not according to its date, but to is structure
     *        in the history branches
     * @return {@code this}
     */
    public LogOp setTopoOrder(boolean topo) {
        this.topo = topo;
        return this;
    }

    /**
     * Sets whether the log should list the first parent of each commit
     * 
     * @param firstParent true if it should show only the first parent
     * @return {@code this}
     */
    public LogOp setFirstParentOnly(boolean firstParent) {
        this.firstParent = firstParent;
        return this;
    }

    /**
     * Adds a commit to be used as starting point for computing history. If no commit is provided,
     * HEAD is used, or the 'until' commit if provided
     * 
     * @param branch the branch to use
     * @return {@code this}
     */
    public LogOp addCommit(ObjectId commit) {
        this.commits.add(commit);
        return this;
    }

    /**
     * Sets the regexp to filter out author names
     * 
     * @param the regexp to use for filtering author names
     * @return {@code this}
     */
    public LogOp setAuthor(String author) {
        this.author = Pattern.compile(author);
        return this;
    }

    /**
     * Sets the regexp to filter out commiter names
     * 
     * @param the regexp to use for filtering commiter names
     * @return {@code this}
     */
    public LogOp setCommiter(String commiter) {
        this.commiter = Pattern.compile(commiter);
        return this;
    }

    /**
     * Show only commits that affect any of the specified paths.
     * 
     * @param path
     * @return {@code this}
     */
    public LogOp addPath(final @NonNull String path) {

        if (this.paths == null) {
            this.paths = new HashSet<>();
        }
        this.paths.add(path);
        return this;
    }

    /**
     * Show only commits that lie within the specified time range.
     * 
     * @param commitRange time range to show commits from
     * @return {@code this}
     */
    public LogOp setTimeRange(final Range<Date> commitRange) {
        if (commitRange == null) {
            this.timeRange = ALWAYS;
        } else {
            this.timeRange = commitRange;
        }
        return this;
    }

    /**
     * Executes the log operation.
     * 
     * @return the list of commits that satisfy the query criteria, most recent first.
     * @see org.locationtech.geogig.repository.impl.AbstractGeoGigOp#call()
     */
    protected @Override Iterator<RevCommit> _call() {
        checkArgument(limit == null || limit > 0, "limit shall be > 0: " + limit);
        checkArgument(skip == null || skip > 0, "skip shall be > 0: " + skip);

        Geogig geogig = geogig();
        ObjectId newestCommitId;
        ObjectId oldestCommitId;
        {
            if (this.until == null) {
                newestCommitId = geogig.refs().head().get().getObjectId();
            } else {
                try {
                    newestCommitId = geogig.commands().resolveCommit(this.until)
                            .map(RevCommit::getId).orElseThrow(() -> new IllegalArgumentException(
                                    "Provided 'until' commit id does not exist: " + until));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            "Provided 'until' commit id does not exist: " + until, e);
                }
            }
            if (this.since == null) {
                oldestCommitId = ObjectId.NULL;
            } else {
                if (!geogig.objects().commitExists(this.since)) {
                    throw new IllegalArgumentException(
                            "Provided 'since' commit id does not exist: " + since.toString());
                }
                oldestCommitId = this.since;
            }
        }

        Iterator<RevCommit> history;
        if (firstParent) {
            history = new LinearHistoryIterator(newestCommitId, geogig);
        } else {
            if (commits.isEmpty()) {
                commits.add(newestCommitId);
            }
            if (topo) {
                history = new TopologicalHistoryIterator(commits, geogig, oldestCommitId);
            } else {
                history = new ChronologicalHistoryIterator(commits, geogig);
            }
        }
        LogFilter filter = new LogFilter(geogig, oldestCommitId, timeRange, paths, author,
                commiter);
        Iterator<RevCommit> filteredCommits = Streams.stream(history).filter(filter).iterator();
        if (skip != null) {
            Iterators.advance(filteredCommits, skip.intValue());
        }
        if (limit != null) {
            filteredCommits = Iterators.limit(filteredCommits, limit.intValue());
        }
        return filteredCommits;
    }

    /**
     * Iterator that traverses the commit history backwards starting from the provided commit, in
     * chronological order. It performs a reverse breadth-first search
     *
     * ChronologicalHistoryIterator walks the Commit Graph and atempts to walk the nodes in
     * Chronological order.
     *
     * In general, if all the commit times are unique (not occurring at the same time) and commit
     * order is in chronological order then this process is simple.
     *
     * However, if commits can occur at the same time or if time can be not always moving forward
     * (i.e. due to changes in the clock or clock skew for a remote) this can be more complex.
     *
     * This method is likely not perfect, however, it will usually be correct (without a lot of
     * extra DB work).
     *
     * It could be improved by using some of the techniques in the Topological operator, however,
     * the extra DB work is not justified since its mostly only useful for giving a "better" order
     * for cases where commits happen at the same time (which is ambiguous).
     *
     * We do, however, store all the nodes that we've seen so we don't re-traverse them. This is
     * quick, and memory efficient (1,000,000 history is about 20M of memory).
     *
     */
    private static class ChronologicalHistoryIterator extends AbstractIterator<RevCommit> {

        private final Geogig repo;

        private Set<RevCommit> parents;

        private Set<ObjectId> seenCommits; // don't re-traverse the same part of the tree

        /**
         * Constructs a new {@code LinearHistoryIterator} with the given parameters.
         *
         * @param tip the first commit in the history
         * @param repo the repository where the commits are stored.
         */

        public ChronologicalHistoryIterator(final List<ObjectId> tips, final Geogig repo) {
            parents = new HashSet<>();
            seenCommits = new HashSet<>();
            for (ObjectId tip : tips) {
                if (!tip.isNull()) {
                    final RevCommit commit = repo.objects().getCommit(tip);
                    if (!parents.contains(commit)) {
                        parents.add(commit);
                    }
                }
            }
            this.repo = repo;
        }

        /**
         * Calculates the next commit in the history.
         *
         * @return the next {@link RevCommit commit} in the history
         */
        protected @Override RevCommit computeNext() {
            if (parents.isEmpty()) {
                return endOfData();
            } else {
                Iterator<RevCommit> iter = parents.iterator();
                RevCommit mostRecent = iter.next();
                while (iter.hasNext()) {
                    RevCommit commit = iter.next();
                    if (mostRecent.getCommitter().getTimestamp() < commit.getCommitter()
                            .getTimestamp()) {
                        mostRecent = commit;
                    }
                }
                parents.remove(mostRecent);
                RevCommit commit;
                for (ObjectId parent : mostRecent.getParentIds()) {
                    if (repo.objects().commitExists(parent)) {
                        commit = repo.objects().getCommit(parent);
                        if (!seenCommits.contains(commit.getId())) {
                            parents.add(commit);
                            seenCommits.add(commit.getId());
                        }
                    }
                }
                return mostRecent;
            }
        }
    }

    /**
     * Iterator that traverses the commit history backwards starting from the provided commit, in
     * topological order. It performs a reverse depth-first search
     * 
     */
    private static class TopologicalHistoryIterator extends AbstractIterator<RevCommit> {

        private final Geogig repo;

        private Stack<RevCommit> tips;

        private RevCommit lastCommit;

        private List<ObjectId> stopPoints;

        private GraphDatabase graphDb;

        private ObjectId oldestCommitId;

        /**
         * Constructs a new {@code LinearHistoryIterator} with the given parameters.
         * 
         * @param tipsList the list of tips to start computing history from
         * @param repo the repository where the commits are stored.
         */
        public TopologicalHistoryIterator(final List<ObjectId> tipsList, final Geogig repo,
                ObjectId oldestCommitId) {
            this.graphDb = repo.graph().db();
            tips = new Stack<RevCommit>();
            stopPoints = new ArrayList<>();
            stopPoints.add(oldestCommitId);
            this.oldestCommitId = oldestCommitId;
            for (ObjectId tip : tipsList) {
                if (!tip.isNull()) {
                    final RevCommit commit = repo.objects().getCommit(tip);
                    tips.add(commit);
                    stopPoints.add(tip);
                }
            }
            this.repo = repo;
        }

        /**
         * Calculates the next commit in the history.
         * 
         * @return the next {@link RevCommit commit} in the history
         */
        protected @Override RevCommit computeNext() {
            if (lastCommit == null) {
                lastCommit = tips.pop();
                return lastCommit;
            }
            Optional<ObjectId> parent = Optional.empty();
            int index = 0;
            for (ObjectId parentId : lastCommit.getParentIds()) {
                if (stopPoints.contains(parentId)) {
                    index++;
                    continue;
                }
                if (repo.objects().commitExists(parentId)) {
                    parent = Optional.of(parentId);
                    break;
                }
                index++;
            }
            if (!parent.isPresent() || parent.get().isNull() || stopPoints.contains(parent.get())) {
                // move to the next tip and start traversing it
                if (tips.isEmpty()) {
                    return endOfData();
                } else {
                    lastCommit = tips.pop();
                    if (!ObjectId.NULL.equals(oldestCommitId)) {
                        // Add the common ancestor of oldestCommitId and the new tip to the
                        // stopPoints to make sure we only hit relevant commits
                        Optional<ObjectId> ancestor = repo.commands()
                                .commonAncestor(lastCommit.getId(), oldestCommitId);
                        if (ancestor.isPresent()) {
                            stopPoints.add(ancestor.get());
                        }
                    }
                }
            } else {
                List<ObjectId> parents = lastCommit.getParentIds();
                for (int i = index + 1; i < parents.size(); i++) {
                    if (repo.objects().commitExists(parents.get(i))) {
                        final RevCommit commit = repo.objects().getCommit(parents.get(i));
                        tips.push(commit);
                    }
                }
                lastCommit = repo.objects().getCommit(parent.get());
            }

            List<ObjectId> children = this.graphDb.getChildren(lastCommit.getId());
            if (children.size() > 1) {
                stopPoints.add(lastCommit.getId());
            }

            return lastCommit;
        }
    }

    /**
     * Iterator that traverses the commit history backwards starting from the provided commit, using
     * only the first parent of each commit
     * 
     */
    private static class LinearHistoryIterator extends AbstractIterator<RevCommit> {

        private Optional<ObjectId> nextCommitId;

        private final Geogig repo;

        /**
         * Constructs a new {@code LinearHistoryIterator} with the given parameters.
         * 
         * @param tip the first commit in the history
         * @param repo the repository where the commits are stored.
         */
        @SuppressWarnings("unchecked")
        public LinearHistoryIterator(final ObjectId tip, final Geogig repo) {
            this.nextCommitId = (Optional<ObjectId>) (tip.isNull() ? Optional.empty()
                    : Optional.of(tip));
            this.repo = repo;
        }

        /**
         * Calculates the next commit in the history.
         * 
         * @return the next {@link RevCommit commit} in the history
         */
        protected @Override RevCommit computeNext() {
            if (nextCommitId.isPresent()) {
                RevCommit commit = repo.objects().getCommit(nextCommitId.get());
                nextCommitId = commit.parentN(0);
                if (nextCommitId.isPresent() && !repo.objects().commitExists(nextCommitId.get())) {
                    nextCommitId = Optional.empty();
                }
                return commit;
            }
            return endOfData();
        }

    }

    /**
     * Checks whether the given commit satisfies all the filter criteria set to this op.
     * 
     * @return {@code true} if the commit satisfies the filter criteria set to this op
     */
    private static class LogFilter implements Predicate<RevCommit> {

        private boolean toReached;

        private final ObjectId oldestCommitId;

        private final Range<Date> timeRange;

        private final Set<String> paths;

        private Pattern author;

        private Pattern committer;

        private FindTreeChild findTreeChild;

        private @NonNull Geogig repo;

        /**
         * Constructs a new {@code LogFilter} with the given parameters.
         * 
         * @param oldestCommitId the oldest commit, exclusive. Indicates when to stop evaluating.
         * @param timeRange extra time range filter besides oldest commit
         * @param paths extra filter on content, indicates to return only commits that affected any
         *        of the provided paths
         * @param commiter the regexp pattern to filter author names
         * @param author the regexp pattern to filter commiter names
         */
        public LogFilter(@NonNull Geogig repo, final @NonNull ObjectId oldestCommitId,
                final @NonNull Range<Date> timeRange, final Set<String> paths, Pattern author,
                Pattern commiter) {
            this.repo = repo;
            this.oldestCommitId = oldestCommitId;
            this.timeRange = timeRange;
            this.author = author;
            this.committer = commiter;
            this.paths = paths;
            findTreeChild = repo.commands().command(FindTreeChild.class);
        }

        /**
         * @return {@code true} if the commit satisfies the filter criteria set to this op
         */
        public @Override boolean test(final RevCommit commit) {
            if (toReached) {
                return false;
            }
            if (oldestCommitId.equals(commit.getId())) {
                toReached = true;
                return false;
            }
            Optional<String> authorName = commit.getAuthor().getName();
            if (author != null && authorName.isPresent()) {
                Matcher authorMatcher = author.matcher(authorName.get());
                if (!authorMatcher.matches()) {
                    return false;
                }
            }
            Optional<String> committerName = commit.getCommitter().getName();
            if (committer != null && committerName.isPresent()) {
                Matcher committerMatcher = committer.matcher(committerName.get());
                if (!committerMatcher.matches()) {
                    return false;
                }
            }
            boolean applies = timeRange.contains(new Date(commit.getCommitter().getTimestamp()));
            if (!applies) {
                return false;
            }
            if (paths != null) {
                applies = false;
                // did this commit touch any of the paths?
                RevTree commitTree = repo.objects().getTree(commit.getTreeId());
                ObjectId currentValue, parentValue;
                for (String path : paths) {
                    currentValue = getPathHash(commitTree, path);
                    // See if the new value is different from any of the parents.
                    int parentIndex = 0;
                    do {
                        ObjectId parentId = commit.parentN(parentIndex++).orElse(ObjectId.NULL);
                        if (parentId.isNull() || !repo.objects().commitExists(parentId)) {
                            // we have reached the bottom of a shallow clone or the end of history.
                            if (!currentValue.isNull()) {
                                applies = true;
                                break;
                            }
                        } else {
                            RevCommit otherCommit = repo.objects().getCommit(parentId);
                            RevTree parentTree = repo.objects().getTree(otherCommit.getTreeId());
                            parentValue = getPathHash(parentTree, path);
                            if (!parentValue.equals(currentValue)) {
                                applies = true;
                                break;
                            }
                        }
                    } while (parentIndex < commit.getParentIds().size());

                    if (applies) {
                        break;
                    }
                }
            }

            return applies;
        }

        private ObjectId getPathHash(RevTree tree, String path) {
            ObjectId hash = ObjectId.NULL;
            Optional<NodeRef> ref = findTreeChild.setChildPath(path).setParent(tree).call();
            if (ref.isPresent()) {
                hash = ref.get().getNode().getObjectId();
            }
            return hash;
        }
    }

}
