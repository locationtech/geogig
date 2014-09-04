/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.locationtech.geogig.api.Bucket;
import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.storage.Deduplicator;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.collect.AbstractIterator;

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
public class PostOrderIterator extends AbstractIterator<RevObject> {

    /**
     * A traversal of all objects reachable from the given origin, with deduplication.
     */
    public static Iterator<RevObject> all(ObjectId top, ObjectDatabase database, Deduplicator deduplicator) {
        List<ObjectId> start = new ArrayList<ObjectId>();
        start.add(top);
        return new PostOrderIterator(start, database, unique(ALL_SUCCESSORS));
    }

    /**
     * A traversal of all objects reachable from the given start list but not reachable from the
     * base list, with deduplication. If the traverseCommits flag is set, then the ancestry of the
     * commits will be traversed as well as the content, otherwise only the content.
     */
    public static Iterator<RevObject> range(List<ObjectId> start, List<ObjectId> base,
            ObjectDatabase database, boolean traverseCommits, Deduplicator deduplicator) {
        return new PostOrderIterator(new ArrayList<ObjectId>(start), database, //
                uniqueWithDeduplicator(blacklist((traverseCommits ? ALL_SUCCESSORS : COMMIT_SUCCESSORS), base), deduplicator));
    }

    /**
     * A traversal of commit history (no content) with deduplication. 
     * @param start
     * @param base
     * @param database
     * @return
     */
    public static Iterator<RevObject> rangeOfCommits(List<ObjectId> start, List<ObjectId> base, ObjectDatabase database, Deduplicator deduplicator) {
        return new PostOrderIterator(new ArrayList<ObjectId>(start), database, uniqueWithDeduplicator(blacklist( COMMIT_PARENTS, base), deduplicator));
    }

    public static Iterator<RevObject> contentsOf(List<ObjectId> needsPrevisit,
            ObjectDatabase database, Deduplicator deduplicator) {
        return new PostOrderIterator(new ArrayList<ObjectId>(needsPrevisit), database, uniqueWithDeduplicator(COMMIT_SUCCESSORS, deduplicator));
    }

    /**
     * A handle to the object database used for the traversal
     */
    private final ObjectDatabase database;

    /**
     * The collection of ObjectIds that must be visited. It is organized as a list of lists - the
     * first entry is always the deepest set of ObjectIds that needs to be processed.
     */
    private List<List<ObjectId>> toVisit;

    /**
     * A flag tracking the state of the traversal. When true, we are building up a queue of objects
     * to visit. When false, we are visiting them (aka returning them from the iterator.)
     */
    private boolean enqueue;

    /**
     * The Successors object determining which objects reachable from the current one to enqueue.
     */
    private final Successors successors;

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
    private PostOrderIterator(List<ObjectId> start, ObjectDatabase database, Successors successors) {
        super();
        this.database = database;
        this.enqueue = true;
        this.successors = successors;
        toVisit = new ArrayList<List<ObjectId>>();
        toVisit.add(new ArrayList<ObjectId>());
        toVisit.get(0).addAll(start);
    }

    @Override
    protected RevObject computeNext() {
        while (!toVisit.isEmpty()) {
            List<ObjectId> currentList = toVisit.get(0);
            if (currentList.isEmpty()) {
                // No more ids at this depth - pop a level off of the stack and switch to "visiting"
                // mode
                enqueue = false;
                toVisit.remove(0);
            } else {
                if (enqueue) {
                    // We're building up a list of objects to visit, so add all the reachable
                    // objects from here to the front of the toVisit stack
                    final ObjectId id = currentList.get(0);
                    final RevObject object = database.get(id);
                    final List<ObjectId> next = new ArrayList<ObjectId>();
                    successors.findSuccessors(object, next);
                    toVisit.add(0, next);
                } else {
                    // We just visited a node, so switch back to enqueuing mode in order to make
                    // sure the successors of the next one at this depth are visited.
                    enqueue = true;
                    final ObjectId id = currentList.remove(0);

                    if (successors.previsit(id)) {
                        return database.get(id);
                    }
                }
            }
        }
        // when the toVisit list becomes empty, we are done
        return endOfData();
    }

    /**
     * The Successors interface defines a pluggable strategy for finding successors of (nodes
     * reachable from) a GeoGig history object. We follow a combinatorial approach in defining
     * Successors - a few immutable basic Successors definitions are provided, and some tools for
     * combining them to produce more complex strategies.
     */
    private static interface Successors {
        /**
         * Calculate the list of ObjectIds for objects directly reachable from the given RevObject
         * according to this policy.
         * 
         * @param object an object whose successor list should be calculated
         * @param successors a List into which successors will be inserted
         */
        public void findSuccessors(RevObject object, List<ObjectId> successors);

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
        public void findSuccessors(final RevObject object, final List<ObjectId> successors) {
            if (object instanceof RevCommit) {
                final RevCommit commit = (RevCommit) object;
                successors.addAll(commit.getParentIds());
            }
        }

        @Override
        public boolean previsit(ObjectId id) {
            return true;
        }
    };

    private final static Successors TAG_COMMIT = new Successors() {
        public void findSuccessors(final RevObject object, final List<ObjectId> successors) {
            if (object instanceof RevTag) {
                final RevTag tag = (RevTag) object;
                successors.add(tag.getCommitId());
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
        public void findSuccessors(final RevObject object, final List<ObjectId> successors) {
            if (object instanceof RevCommit) {
                final RevCommit commit = (RevCommit) object;
                successors.add(commit.getTreeId());
            }
        }

        @Override
        public boolean previsit(ObjectId id) {
            return true;
        }
    };

    /**
     * A Successors strategy for traversing to features from a tree node
     */
    private final static Successors TREE_FEATURES = new Successors() {
        public void findSuccessors(final RevObject object, final List<ObjectId> successors) {
            if (object instanceof RevTree) {
                final RevTree tree = (RevTree) object;
                if (tree.features().isPresent()) {
                    final Set<ObjectId> seen = new HashSet<ObjectId>();
                    for (Node n : tree.features().get()) {
                        if (n.getMetadataId().isPresent()) {
                            if (seen.add(n.getMetadataId().get())) {
                                successors.add(n.getMetadataId().get());
                            }
                        }
                        if (seen.add(n.getObjectId())) {
                            successors.add(n.getObjectId());
                        }
                    }
                }
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
        public void findSuccessors(final RevObject object, final List<ObjectId> successors) {
            if (object instanceof RevTree) {
                final RevTree tree = (RevTree) object;
                if (tree.trees().isPresent()) {
                    final Set<ObjectId> seen = new HashSet<ObjectId>();
                    for (Node n : tree.trees().get()) {
                        if (n.getMetadataId().isPresent()) {
                            if (seen.add(n.getMetadataId().get())) {
                                successors.add(n.getMetadataId().get());
                            }
                        }
                        if (seen.add(n.getObjectId())) {
                            successors.add(n.getObjectId());
                        }
                    }
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
        public void findSuccessors(final RevObject object, final List<ObjectId> successors) {
            if (object instanceof RevTree) {
                final RevTree tree = (RevTree) object;
                if (tree.buckets().isPresent()) {
                    for (Map.Entry<?, Bucket> entry : tree.buckets().get().entrySet()) {
                        final Bucket bucket = entry.getValue();
                        successors.add(bucket.id());
                    }
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
            public void findSuccessors(final RevObject object, final List<ObjectId> successors) {
                for (Successors s : chained) {
                    s.findSuccessors(object, successors);
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
        return uniqueWithDeduplicator(delegate, new org.locationtech.geogig.storage.memory.HeapDeduplicator());
    }
    
    private final static Successors uniqueWithDeduplicator(final Successors delegate, final Deduplicator deduplicator) {
        return new Successors() {
            public void findSuccessors(final RevObject object, final List<ObjectId> successors) {
                if (!deduplicator.isDuplicate(object.getId())) {
                    final int oldSize = successors.size();
                    delegate.findSuccessors(object, successors);
                    deduplicator.removeDuplicates(successors.subList(oldSize, successors.size()));
                }
            }

            public boolean previsit(ObjectId id) {
                return (!deduplicator.visit(id)) && delegate.previsit(id);
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
    private final static Successors blacklist(final Successors delegate, final List<ObjectId> base) {
        final Set<ObjectId> baseSet = new HashSet<ObjectId>(base);
        return new Successors() {
            public void findSuccessors(final RevObject object, final List<ObjectId> successors) {
                if (!baseSet.contains(object.getId())) {
                    final int oldSize = successors.size();
                    delegate.findSuccessors(object, successors);
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
