/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.pack;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.locationtech.geogig.model.RevTree.EMPTY;
import static org.locationtech.geogig.model.RevTree.EMPTY_TREE_ID;
import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk;
import org.locationtech.geogig.plumbing.diff.PreOrderDiffWalk.BucketIndex;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.BulkOpListener.CountingListener;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;

import com.google.common.base.Optional;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

class PackImpl implements Pack {

    private final Repository source;

    /**
     * All missing commits, by {@link RefRequest}, with no duplication (i.e. if a commit is part of
     * two missing ref histories, it'll be present only on the first one, according to the map's
     * iteration order)
     */
    private final LinkedHashMap<RefRequest, List<RevCommit>> missingCommits;

    private final List<RevTag> missingTags;

    protected PackImpl(Repository source, List<RevTag> missingTags,
            LinkedHashMap<RefRequest, List<RevCommit>> missingCommits) {
        checkNotNull(source);
        checkNotNull(missingTags);
        checkNotNull(missingCommits);

        this.source = source;
        this.missingTags = missingTags;
        this.missingCommits = missingCommits;
    }

    public @Override List<RefDiff> applyTo(PackProcessor target, ProgressListener progress) {
        checkNotNull(target);
        checkNotNull(progress);

        IdFilter visitedIds = new IdFilter();

        List<RefDiff> result = new ArrayList<>();
        List<RefRequest> reqs = Lists.newArrayList(missingCommits.keySet());
        for (RefRequest req : reqs) {
            RefDiff changedRef = applyToPreOrder(target, req, visitedIds, progress);
            checkNotNull(changedRef);
            result.add(changedRef);
        }

        List<RevTag> tags = this.missingTags;
        target.putAll(tags.iterator(), NOOP_LISTENER);
        return result;
    }

    private static class AppliedProgressIndicator implements Function<ProgressListener, String> {

        private final ObjectReporter processed;

        private final CountingListener inserted;

        AppliedProgressIndicator(ObjectReporter processed, CountingListener inserted) {
            this.processed = processed;
            this.inserted = inserted;
        }

        public @Override String apply(final ProgressListener unused) {
            return String.format("inserted %,d/%,d. %s", inserted.inserted(), processed.total.get(),
                    processed);
        }
    }

    private RefDiff applyToPreOrder(PackProcessor target, RefRequest req, IdFilter visitedIds,
            ProgressListener progress) {

        progress.started();

        progress.setDescription("Applying changes to " + req.name);
        ObjectReporter objectReport = new ObjectReporter(progress);

        // back up current progress indicator
        final Function<ProgressListener, String> defaultProgressIndicator;
        defaultProgressIndicator = progress.progressIndicator();
        // set our custom progress indicator
        final CountingListener listener = BulkOpListener.newCountingListener();
        progress.setProgressIndicator(new AppliedProgressIndicator(objectReport, listener));

        final List<RevCommit> commits = missingCommits.get(req);
        checkNotNull(commits);

        final ObjectDatabase sourceStore = source.objectDatabase();
        final Producer producer = new Producer(sourceStore, target, commits, visitedIds,
                objectReport);

        final ExecutorService producerThread = Executors.newSingleThreadExecutor();
        try {
            producerThread.submit(producer);
            Iterator<RevObject> objects = producer.iterator();

            final Stopwatch sw = Stopwatch.createStarted();

            target.putAll(objects, listener);
            progress.complete();
            progress.started();
            progress.setDescription(String.format("Objects inserted: %,d, repeated: %,d, time: %s",
                    listener.inserted(), listener.found(), sw.stop()));
        } finally {
            producerThread.shutdownNow();
            // restore previous progress indicator
            progress.setProgressIndicator(defaultProgressIndicator);
        }

        Ref oldRef = req.have.isPresent() ? new Ref(req.name, req.have.get()) : null;
        Ref newRef = new Ref(req.name, req.want);
        RefDiff changedRef = new RefDiff(oldRef, newRef);

        progress.complete();

        return changedRef;
    }

    private class Producer implements Consumer<ObjectId>, Runnable {

        private final ObjectStore source;

        private final PackProcessor target;

        private final IdFilter visitedIds;

        private LinkedBlockingQueue<ObjectId> queue = new LinkedBlockingQueue<>(1_000_000);

        private final List<RevCommit> commits;

        private final ObjectReporter objectReport;

        Producer(ObjectStore source, PackProcessor target, List<RevCommit> commits,
                IdFilter visitedIds, ObjectReporter objectReport) {
            this.source = source;
            this.target = target;
            this.commits = commits;
            this.visitedIds = visitedIds;
            this.objectReport = objectReport;
        }

        public Iterator<RevObject> iterator() {
            Iterator<ObjectId> objectIds = new BlockingIterator<ObjectId>(queue, ObjectId.NULL);
            Iterator<RevObject> objects = source.getAll(() -> objectIds);
            Iterator<RevObject> commitsIterator = new AbstractIterator<RevObject>() {
                private Iterator<RevCommit> it = commits.iterator();

                @Override
                protected RevObject computeNext() {
                    if (it.hasNext()) {
                        objectReport.addCommit();
                        return it.next();
                    }
                    return endOfData();
                }
            };
            return Iterators.concat(objects, commitsIterator);
        }

        public @Override void run() {
            final Map<ObjectId, RevCommit> commitsById = Maps.uniqueIndex(commits,
                    (c) -> c.getId());

            for (RevCommit commit : commits) {
                List<ObjectId> parentIds = commit.getParentIds();
                if (parentIds.isEmpty()) {
                    parentIds = ImmutableList.of(ObjectId.NULL);
                }
                for (ObjectId parentId : parentIds) {
                    RevCommit parent = parentId.isNull() ? null
                            : Optional.fromNullable(commitsById.get(parentId))
                                    .or(() -> source.getCommit(parentId));

                    visitPreOrder(parent, commit, visitedIds, target, objectReport, this);
                }
            }

            accept(ObjectId.NULL);// terminal token
        }

        public @Override void accept(ObjectId id) {
            try {
                queue.put(id);
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw Throwables.propagate(e);
            }
        }
    }

    private void visitPreOrder(@Nullable RevCommit parent, RevCommit commit, IdFilter visited,
            PackProcessor target, ObjectReporter progress, Consumer<ObjectId> consumer) {

        // System.err.printf("processing %s against parent %s\n", commit, parent);

        final ObjectId leftRootId = parent == null ? EMPTY_TREE_ID : parent.getTreeId();
        final ObjectId rightRootId = commit.getTreeId();

        if (visited.contains(rightRootId)) {
            return;
        }

        final ObjectStore source = this.source.objectDatabase();
        final RevTree left = EMPTY;// EMPTY_TREE_ID.equals(leftRootId) ? EMPTY :
                                   // source.getTree(leftRootId);
        final RevTree right = EMPTY_TREE_ID.equals(rightRootId) ? EMPTY
                : source.getTree(rightRootId);

        PreOrderDiffWalk walk = new PreOrderDiffWalk(left, right, source, source, false);

        walk.walk(new PreOrderDiffWalk.AbstractConsumer() {

            private boolean add(ObjectId objectId) {
                if (visited.add(objectId)) {
                    consumer.accept(objectId);
                    return true;
                }
                return false;
            }

            public @Override boolean feature(@Nullable NodeRef left, @Nullable NodeRef right) {
                addFeature(progress, right);
                return true;
            }

            public @Override boolean tree(@Nullable NodeRef left, @Nullable NodeRef right) {
                boolean r = addTree(progress, right);
                return r;
            }

            public @Override boolean bucket(NodeRef leftParent, NodeRef rightParent,
                    BucketIndex bucketIndex, @Nullable Bucket left, @Nullable Bucket right) {
                boolean r = addBucket(progress, right);
                return r;
            }

            private boolean addFeature(ObjectReporter progress, NodeRef right) {
                if (right != null && add(right.getObjectId())) {
                    progress.addFeature();
                    if (right.getNode().getMetadataId().isPresent()) {
                        ObjectId md = right.getNode().getMetadataId().get();
                        if (add(md)) {
                            progress.addFeatureType();
                        }
                    }
                }
                return true;
            }

            private boolean addTree(ObjectReporter progress, NodeRef right) {
                if (right != null && add(right.getObjectId())) {
                    progress.addTree();
                    if (right.getNode().getMetadataId().isPresent()) {
                        ObjectId md = right.getNode().getMetadataId().get();
                        if (add(md)) {
                            progress.addFeatureType();
                        }
                    }
                    return true;
                }
                return false;
            }

            private boolean addBucket(ObjectReporter progress, Bucket right) {
                if (right != null && add(right.getObjectId())) {
                    progress.addBucket();
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Naive {@code Set<ObjectId>} visited set implementation, might be replaced by a bloom or
     * cuckoo filter based one.
     */
    private static class IdFilter {
        private Set<ObjectId> visited = Sets.newConcurrentHashSet();

        public boolean add(ObjectId id) {
            return visited.add(id);
        }

        public boolean contains(ObjectId id) {
            return visited.contains(id);
        }
    }

    private static class ObjectReporter {

        final AtomicLong total = new AtomicLong();

        final AtomicLong tags = new AtomicLong();

        final AtomicLong commits = new AtomicLong();

        final AtomicLong trees = new AtomicLong();

        final AtomicLong buckets = new AtomicLong();

        final AtomicLong features = new AtomicLong();

        final AtomicLong featureTypes = new AtomicLong();

        final ProgressListener progress;

        public ObjectReporter(ProgressListener progress) {
            this.progress = progress;
        }

        public void addTree() {
            increment(trees);
        }

        public void addTag() {
            increment(tags);
        }

        public void addBucket() {
            increment(buckets);
        }

        public void addFeature() {
            increment(features);
        }

        public void addFeatureType() {
            increment(featureTypes);
        }

        public void addCommit() {
            increment(commits);
        }

        private void increment(AtomicLong counter) {
            counter.incrementAndGet();
            total.incrementAndGet();
            notifyProgressListener();
        }

        private void notifyProgressListener() {
            progress.setProgress(progress.getProgress() + 1);
        }

        public void complete() {
            progress.complete();
        }

        public @Override String toString() {
            return String.format(
                    "commits: %,d, trees: %,d, buckets: %,d, features: %,d, ftypes: %,d",
                    commits.get(), trees.get(), buckets.get(), features.get(), featureTypes.get());
        }
    }

    private static class BlockingIterator<T> extends AbstractIterator<T> {

        private final BlockingQueue<T> queue;

        private final T terminalToken;

        public BlockingIterator(BlockingQueue<T> queue, T terminalToken) {
            this.queue = queue;
            this.terminalToken = terminalToken;
        }

        @Override
        protected T computeNext() {
            T object;
            try {
                object = queue.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
                throw Throwables.propagate(e);
            }
            if (terminalToken.equals(object)) {
                return endOfData();
            }
            return object;
        }
    }

}
