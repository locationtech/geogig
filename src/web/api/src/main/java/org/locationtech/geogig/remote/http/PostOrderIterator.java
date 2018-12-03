/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remote.http;

import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.remotes.internal.Deduplicator;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * The PostOrderIterator class provides utilities for traversing a GeoGig revision history graph in
 * postorder or depth-first order. In the context of a GeoGig revision this means that if objects A
 * and B are both in the subgraph being traversed and A references B, then B will be visited before
 * A.
 * 
 * PostOrderIterator should not be constructed directly, but rather instantiated via static factory
 * methods provided for specific configurations. Performance characteristics vary according to the
 * traversal policy. In general, an iterator must track all the object ids that have been enqueued,
 * but not yet visited, so it will require memory at least proportional to the depth (in a traversal
 * of all objects in a repository, this means all objects along the path from the newest commit
 * being traversed to the oldest commit being traversed and through the tree structure at that
 * commit to the features.) Some traversals use additional memory to avoid re-visiting objects that
 * are reachable via multiple paths (eg, features that are part of multiple commits.)
 */
class PostOrderIterator extends AbstractIterator<RevObject> {

    /**
     * A traversal of all objects reachable from the given origin, with deduplication.
     */
    public static Iterator<RevObject> all(ObjectId top, ObjectStore database,
            Deduplicator deduplicator) {
        List<ObjectId> start = new ArrayList<ObjectId>();
        start.add(top);
        return new PostOrderIterator(start, database,
                uniqueWithDeduplicator(ALL_SUCCESSORS, deduplicator));
    }

    /**
     * A traversal of all objects reachable from the given start list but not reachable from the
     * base list, with deduplication. If the traverseCommits flag is set, then the ancestry of the
     * commits will be traversed as well as the content, otherwise only the content.
     */
    public static Iterator<RevObject> range(List<ObjectId> start, List<ObjectId> base,
            ObjectStore database, boolean traverseCommits, Deduplicator deduplicator) {
        return new PostOrderIterator(new ArrayList<ObjectId>(start), database, //
                uniqueWithDeduplicator(
                        blacklist((traverseCommits ? ALL_SUCCESSORS : COMMIT_SUCCESSORS), base),
                        deduplicator));
    }

    /**
     * A traversal of commit history (no content) with deduplication.
     * 
     * @param start
     * @param base
     * @param database
     * @return
     */
    public static Iterator<RevObject> rangeOfCommits(List<ObjectId> start, List<ObjectId> base,
            ObjectStore database, Deduplicator deduplicator) {
        return new PostOrderIterator(new ArrayList<ObjectId>(start), database,
                uniqueWithDeduplicator(blacklist(COMMIT_PARENTS, base), deduplicator));
    }

    public static Iterator<RevObject> contentsOf(List<ObjectId> needsPrevisit, ObjectStore database,
            Deduplicator deduplicator) {
        return new PostOrderIterator(new ArrayList<ObjectId>(needsPrevisit), database,
                uniqueWithDeduplicator(COMMIT_SUCCESSORS, deduplicator));
    }

    /**
     * A handle to the object database used for the traversal
     */
    private final ObjectStore database;

    /**
     * A {@link PostOrderIteratorInternal} providing a stream of {@link RevObject} and/or
     * {@link ObjectId} instances
     * 
     * @see #computeNext()
     */
    private final Iterator<Object> internal;

    /**
     * The current batch of {@link RevObject} instances resolved from {@link #internal} that
     * {@link #computeNext()} returns from
     */
    private Iterator<RevObject> currentBatch = Collections.emptyIterator();

    /**
     * The single, private constructor for PostOrderIterator. Generally it will be more convenient
     * to use static factory methods which properly prepare a Successors capturing the traversal
     * policy for the iteration.
     * 
     * @param start the initial list of objects to expand out from (these will be the last ones
     *        actually visited, but the subgraph visited is reachable from this list)
     * @param database the objectdatabase used for retrieving objects
     * @param successors the traversal policy for this iteration.
     */
    private PostOrderIterator(List<ObjectId> start, ObjectStore database, Successors successors) {
        this.database = database;
        this.internal = new PostOrderIteratorInternal(start, database, successors);
    }

    /**
     * Returns objects from {@link #currentBatch}, creating a new batch from {@link #internal} while
     * there are objects in {@link #internal}
     * <p>
     * {@link #internal} is a {@link PostOrderIteratorInternal}
     * <p>
     * Since {@link #internal} may return either {@link RevObject} instances or {@link ObjectId}
     * instances, sequences of {@link ObjectId} will first be translated to
     * {@link ObjectStore#getAll} calls.
     * 
     * @see PostOrderIteratorInternal
     */
    @Override
    protected RevObject computeNext() {
        if (currentBatch.hasNext()) {
            return currentBatch.next();
        }
        if (internal.hasNext()) {
            currentBatch = computeNextBatch();
            return computeNext();
        }
        return endOfData();
    }

    private Iterator<RevObject> computeNextBatch() {
        // used to preserve order of elements returned by #internal
        LinkedHashMap<ObjectId, RevObject> ordered = new LinkedHashMap<>();

        List<ObjectId> query = new ArrayList<>();

        while (internal.hasNext()) {
            Object next = internal.next();
            if (next instanceof RevObject) {
                RevObject o = (RevObject) next;
                ordered.put(o.getId(), o);
            } else {
                ObjectId id = (ObjectId) next;
                query.add(id);
                ordered.put(id, null);
            }
            if (ordered.size() == 1000) {
                break;
            }
        }

        if (!query.isEmpty()) {
            Iterator<RevObject> all = database.getAll(query);
            all.forEachRemaining((o) -> ordered.put(o.getId(), o));
        }

        return ordered.values().iterator();
    }

    /**
     * Uses {@link Successors} to traverse all the reachable objects in the commit graph and produce
     * an iterator of objects that are either {@link RevObject} or {@link ObjectId} instances.
     * <p>
     * This is so {@link RevFeature} instances don't need to be pre-fetches unnecessarily before
     * going through the {@link Successors#previsit(ObjectId)} filter. That is, all
     * {@link Successors} instances return {@link RevObject}s except {@link #TREE_FEATURES}
     *
     */
    private static class PostOrderIteratorInternal extends AbstractIterator<Object> {
        /**
         * A handle to the object database used for the traversal
         */
        private final ObjectStore database;

        /**
         * The collection of ObjectIds that must be visited. It is organized as a list of lists -
         * the first entry is always the deepest set of ObjectIds that needs to be processed.
         */
        private List<List<Object>> toVisit;

        /**
         * A flag tracking the state of the traversal. When true, we are building up a queue of
         * objects to visit. When false, we are visiting them (aka returning them from the
         * iterator.)
         */
        private boolean enqueue;

        /**
         * The Successors object determining which objects reachable from the current one to
         * enqueue.
         */
        private final Successors successors;

        private PostOrderIteratorInternal(List<ObjectId> start, ObjectStore database,
                Successors successors) {
            this.database = database;
            this.enqueue = true;
            this.successors = successors;
            toVisit = new ArrayList<List<Object>>();
            Iterator<RevObject> startobjs = database.getAll(start);
            ArrayList<Object> startList = Lists.newArrayList(startobjs);
            toVisit.add(startList);
        }

        @Override
        protected Object computeNext() {
            while (!toVisit.isEmpty()) {
                List<Object> currentList = toVisit.get(0);
                if (currentList.isEmpty()) {
                    // No more ids at this depth - pop a level off of the stack and switch to
                    // "visiting"
                    // mode
                    enqueue = false;
                    toVisit.remove(0);
                } else {
                    if (enqueue) {
                        // We're building up a list of objects to visit, so add all the reachable
                        // objects from here to the front of the toVisit stack
                        final Object object = currentList.get(0);
                        final List<Object> next = new ArrayList<Object>();
                        successors.findSuccessors(object, next, database);
                        toVisit.add(0, next);
                    } else {
                        // We just visited a node, so switch back to enqueuing mode in order to make
                        // sure the successors of the next one at this depth are visited.
                        enqueue = true;
                        final Object obj = currentList.remove(0);
                        final ObjectId id = id(obj);
                        if (successors.previsit(id)) {
                            return obj;
                        }
                    }
                }
            }
            // when the toVisit list becomes empty, we are done
            return endOfData();
        }
    }

    private static ObjectId id(Object object) {
        return object instanceof RevObject ? ((RevObject) object).getId() : (ObjectId) object;
    }

    /**
     * The Successors interface defines a pluggable strategy for finding successors of (nodes
     * reachable from) a GeoGig history object. We follow a combinatorial approach in defining
     * Successors - a few immutable basic Successors definitions are provided, and some tools for
     * combining them to produce more complex strategies.
     */
    private static interface Successors {
        /**
         * Calculate the list of objects directly reachable from the given RevObject according to
         * this policy.
         * <p>
         * The objects added to the {@code successors} argument list can be {@link RevObject} or
         * {@link ObjectId} instances. This is so that a {@code Successors} implementation can
         * decide whether it's best to pre-fetch the objects from the {@code database} or leave it
         * for a later phase.
         * 
         * @param object an object whose successor list should be calculated
         * @param successors a List into which successors will be inserted
         */
        public void findSuccessors(Object object, List<Object> successors, ObjectStore database);

        /**
         * Test an ObjectId before the object is visited. Implementors should return true if this
         * object should be returned to the client code, false if it should be omitted from results.
         * 
         * @param id the ObjectId of an object that is about to be returned by the iterator
         * @return true iff it should be exposed, and false otherwise.
         */
        public boolean previsit(ObjectId id);
    }

    /**
     * A Successors strategy for traversing to the parents of commit nodes.
     */
    private final static Successors COMMIT_PARENTS = new Successors() {
        public @Override void findSuccessors(final Object object, final List<Object> successors,
                ObjectStore database) {
            if (object instanceof RevCommit) {
                final RevCommit commit = (RevCommit) object;
                Iterator<RevCommit> parents = database.getAll(commit.getParentIds(), NOOP_LISTENER,
                        RevCommit.class);
                parents.forEachRemaining((o) -> successors.add(o));
            }
        }

        @Override
        public boolean previsit(ObjectId id) {
            return true;
        }
    };

    private final static Successors TAG_COMMIT = new Successors() {
        public @Override void findSuccessors(final Object object, final List<Object> successors,
                ObjectStore database) {
            if (object instanceof RevTag) {
                final RevTag tag = (RevTag) object;
                successors.add(database.getCommit(tag.getCommitId()));
            }
        }

        @Override
        public boolean previsit(ObjectId id) {
            return true;
        }
    };

    /**
     * A Successors strategy for traversing to the single content tree from a commit node.
     */
    private final static Successors COMMIT_TREE = new Successors() {
        public @Override void findSuccessors(final Object object, final List<Object> successors,
                ObjectStore database) {
            if (object instanceof RevCommit) {
                successors.add(database.getTree(((RevCommit) object).getTreeId()));
            }
        }

        @Override
        public boolean previsit(ObjectId id) {
            return true;
        }
    };

    /**
     * A Successors strategy for traversing to features from a tree node
     * <p>
     * Adds {@link RevTree#features()}'s {@link ObjectId}s to the {@code successors} list in order
     * to avoid pre-fetching feature instances before their ids pass through the
     * {@link Successors#previsit(ObjectId)} filter
     */
    private final static Successors TREE_FEATURES = new Successors() {
        public @Override void findSuccessors(final Object object, final List<Object> successors,
                ObjectStore database) {
            if (!(object instanceof RevTree)) {
                return;
            }
            final RevTree tree = (RevTree) object;
            if (tree.featuresSize() > 0) {
                final Set<ObjectId> seen = Sets.newHashSet();
                tree.forEachFeature((n) -> {
                    if (n.getMetadataId().isPresent()) {
                        seen.add(n.getMetadataId().get());
                    }
                    seen.add(n.getObjectId());
                });
                successors.addAll(seen);
            }
        }

        @Override
        public boolean previsit(ObjectId id) {
            return true;
        }
    };

    /**
     * A Successors strategy for traversing to subtrees from a tree node
     */
    private final static Successors TREE_SUBTREES = new Successors() {
        public @Override void findSuccessors(final Object object, final List<Object> successors,
                ObjectStore database) {
            if (object instanceof RevTree) {
                final RevTree tree = (RevTree) object;
                if (tree.treesSize() > 0) {
                    final Set<ObjectId> seen = new HashSet<ObjectId>();
                    tree.forEachTree((n) -> {
                        if (n.getMetadataId().isPresent()) {
                            seen.add(n.getMetadataId().get());
                        }
                        seen.add(n.getObjectId());
                    });
                    Iterator<RevObject> all = database.getAll(seen);
                    all.forEachRemaining((o) -> successors.add(o));
                }
            }
        }

        @Override
        public boolean previsit(ObjectId id) {
            return true;
        }
    };

    /**
     * A Successors strategy for traversing to bucket contents from a tree node.
     */
    private final static Successors TREE_BUCKETS = new Successors() {
        public @Override void findSuccessors(final Object object, final List<Object> successors,
                ObjectStore database) {
            if (object instanceof RevTree) {
                final RevTree tree = (RevTree) object;
                if (tree.bucketsSize() > 0) {
                    List<ObjectId> ids = new ArrayList<>(tree.bucketsSize());
                    tree.forEachBucket(bucket -> ids.add(bucket.getObjectId()));
                    Iterator<RevTree> buckets = database.getAll(ids, NOOP_LISTENER, RevTree.class);
                    buckets.forEachRemaining(successors::add);
                }
            }
        }

        @Override
        public boolean previsit(ObjectId id) {
            return true;
        }
    };

    /**
     * A factory method for combining zero or more Successors strategies by producing a strategy
     * visiting all nodes that would be visited by any of the strategies.
     * 
     * @param chained zero or more Successors strategies
     * @return a Successors strategy that visits a node if any constituent strategy would visit that
     *         node.
     */
    private final static Successors combine(final Successors... chained) {
        return new Successors() {
            public @Override void findSuccessors(final Object object, final List<Object> successors,
                    ObjectStore database) {
                for (Successors s : chained) {
                    s.findSuccessors(object, successors, database);
                }
            }

            public boolean previsit(ObjectId id) {
                for (Successors s : chained) {
                    if (!s.previsit(id))
                        return false;
                }
                return true;
            }
        };
    }

    /**
     * A factory method for decorating a Successors strategy with uniqueness checking. The
     * uniqueness check is implemented by caching the ids of all visited objects - this is exact but
     * produces unbounded memory usage.
     * 
     * @param delegate the original Successors strategy
     * @return a modified Successors strategy that visits all the same nodes but filters out any
     *         repetitions.
     */
    private final static Successors unique(final Successors delegate) {
        return uniqueWithDeduplicator(delegate,
                new org.locationtech.geogig.remotes.internal.HeapDeduplicator());
    }

    private final static Successors uniqueWithDeduplicator(final Successors delegate,
            final Deduplicator deduplicator) {
        return new Successors() {
            public @Override void findSuccessors(final Object object, final List<Object> successors,
                    ObjectStore database) {
                ObjectId id = id(object);
                if (!deduplicator.isDuplicate(id)) {
                    final int oldSize = successors.size();
                    delegate.findSuccessors(object, successors, database);
                    List<Object> subList = successors.subList(oldSize, successors.size());
                    deduplicator.removeDuplicates(Lists.transform(subList, (o) -> id(o)));
                }
            }

            public boolean previsit(ObjectId id) {
                return deduplicator.visit(id) && delegate.previsit(id);
            }
        };
    }

    /**
     * A factory method for decorating a Successors strategy with a blacklist. Not only will objects
     * in the blacklist be skipped, but also no objects reachable from them will be visited, unless
     * they are reachable by another path.
     * 
     * @param delegate the original Successors policy
     * @param base a list of blacklisted objectids
     * @return a Successors policy for visiting the same nodes as the original policy, but with
     */
    private final static Successors blacklist(final Successors delegate,
            final List<ObjectId> base) {
        final Set<ObjectId> baseSet = new HashSet<ObjectId>(base);
        return new Successors() {
            public @Override void findSuccessors(final Object object, final List<Object> successors,
                    ObjectStore database) {
                ObjectId id = id(object);
                if (!baseSet.contains(id)) {
                    final int oldSize = successors.size();
                    delegate.findSuccessors(object, successors, database);
                    successors.subList(oldSize, successors.size()).removeAll(baseSet);
                }
            }

            public boolean previsit(ObjectId id) {
                boolean dprevisit = delegate.previsit(id);
                return dprevisit && !baseSet.contains(id);
            }
        };
    }

    /**
     * A traversal policy for visiting all reachable nodes without deduplication
     */
    private final static Successors ALL_SUCCESSORS = combine( //
            TAG_COMMIT, //
            COMMIT_PARENTS, //
            COMMIT_TREE, //
            TREE_BUCKETS, //
            TREE_SUBTREES, //
            TREE_FEATURES);

    /**
     * A traversal policy for visiting all reachable commits without deduplication
     */
    private final static Successors COMMIT_SUCCESSORS = combine( //
            COMMIT_TREE, //
            TREE_BUCKETS, //
            TREE_SUBTREES, //
            TREE_FEATURES);
}
