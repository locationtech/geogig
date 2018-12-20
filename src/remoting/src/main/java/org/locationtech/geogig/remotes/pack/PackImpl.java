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
import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.remotes.RefDiff;
import org.locationtech.geogig.remotes.internal.DeduplicationService;
import org.locationtech.geogig.remotes.internal.Deduplicator;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.IndexDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

class PackImpl implements Pack {

    private final Repository source;

    /**
     * All missing commits, by {@link RefRequest}, with no duplication (i.e. if a commit is part of
     * two missing ref histories, it'll be present only on the first one, according to the map's
     * iteration order)
     */
    private final LinkedHashMap<RefRequest, List<RevCommit>> missingCommits;

    private LinkedHashMap<RefRequest, List<IndexDef>> missingIndexes;

    private final List<RevTag> missingTags;

    protected PackImpl(Repository source, List<RevTag> missingTags,
            LinkedHashMap<RefRequest, List<RevCommit>> missingCommits,
            LinkedHashMap<RefRequest, List<IndexDef>> missingIndexes) {
        checkNotNull(source);
        checkNotNull(missingTags);
        checkNotNull(missingCommits);
        checkNotNull(missingIndexes);

        this.source = source;
        this.missingTags = missingTags;
        this.missingCommits = missingCommits;
        this.missingIndexes = missingIndexes;
    }

    public @Override List<RefDiff> applyTo(PackProcessor target, ProgressListener progress) {
        checkNotNull(target);
        checkNotNull(progress);

        progress.started();

        List<RefDiff> appliedDiffs = new ArrayList<>();
        List<RefRequest> reqs = Lists.newArrayList(missingCommits.keySet());

        Deduplicator deduplicator = DeduplicationService.create();
        try {
            for (RefRequest req : reqs) {
                RefDiff changedRef = applyToPreOrder(target, req, deduplicator, progress);
                checkNotNull(changedRef);
                appliedDiffs.add(changedRef);
            }
        } finally {
            deduplicator.release();
        }

        List<RevTag> tags = this.missingTags;
        target.putAll(tags.iterator(), NOOP_LISTENER);

        // process indexes
        if (!missingIndexes.isEmpty()) {
            reqs = Lists.newArrayList(missingIndexes.keySet());
            deduplicator = DeduplicationService.create();
            try {
                for (RefRequest req : reqs) {
                    applyIndex(target, req, deduplicator, progress);
                }
            } finally {
                deduplicator.release();
            }
        }
        progress.complete();

        return appliedDiffs;
    }

    private RefDiff applyToPreOrder(PackProcessor target, RefRequest req, Deduplicator deduplicator,
            ProgressListener progress) {

        progress.setDescription("Saving missing revision objects changes for " + req.name);
        ObjectReporter objectReport = new ObjectReporter(progress);

        // back up current progress indicator
        final Function<ProgressListener, String> defaultProgressIndicator;
        defaultProgressIndicator = progress.progressIndicator();
        // set our custom progress indicator
        progress.setProgressIndicator((p) -> objectReport.toString());

        final List<RevCommit> commits = missingCommits.get(req);
        checkNotNull(commits);

        final ObjectDatabase sourceStore = source.objectDatabase();

        List<ObjectId[]> diffRootTreeIds = collectMissingRootTreeIdPairs(commits, sourceStore);

        final ContentIdsProducer producer = ContentIdsProducer.forCommits(sourceStore,
                diffRootTreeIds, deduplicator, objectReport);

        final ExecutorService producerThread = Executors.newSingleThreadExecutor();
        try {
            producerThread.submit(producer);
            Iterator<ObjectId> missingContentIds = producer.iterator();

            Iterator<RevObject> allObjects;
            {
                Iterator<RevObject> missingContents;
                Iterator<RevCommit> commitsIterator;
                missingContents = sourceStore.getAll(() -> missingContentIds);

//                (c) -> {
//                    objectReport.addCommit();
//                    return true;
//                }
                Predicate<RevCommit> fn =  new Predicate<RevCommit>() {
                    @Override
                    public boolean apply(RevCommit c) {
                        objectReport.addCommit();
                        return true;
                    }};

                commitsIterator = Iterators.filter(commits.iterator(), fn);

                allObjects = Iterators.concat(missingContents, commitsIterator);
            }
            final Stopwatch sw = Stopwatch.createStarted();

            target.putAll(allObjects, objectReport);
            progress.complete();
            if (objectReport.total.get() > 0) {
                progress.started();
                String description = String.format("Objects inserted: %,d, repeated: %,d, time: %s",
                        objectReport.inserted(), objectReport.found(), sw.stop());
                progress.setDescription(description);
            }
        } finally {
            producerThread.shutdownNow();
            // restore previous progress indicator
            progress.setProgressIndicator(defaultProgressIndicator);
        }

        Ref oldRef = req.have.isPresent() ? new Ref(req.name, req.have.get()) : null;
        Ref newRef = new Ref(req.name, req.want);
        RefDiff changedRef = new RefDiff(oldRef, newRef);

        return changedRef;
    }

    private List<ObjectId[]> collectMissingRootTreeIdPairs(List<RevCommit> commits,
            ObjectDatabase sourceStore) {

        //RevObject::getId, but friendly for Fortify
        com.google.common.base.Function<RevCommit, ObjectId> fn_getId =  new com.google.common.base.Function<RevCommit, ObjectId>() {
            @Override
            public ObjectId apply(RevCommit revobj) {
                return revobj.getId();
            }};

        final Map<ObjectId, RevCommit> rootsById = new HashMap<>(
                Maps.uniqueIndex(commits,fn_getId));

        List<ObjectId[]> diffRootTreeIds = new ArrayList<>();

        for (RevCommit commit : commits) {

            final ObjectId rightTreeId = commit.getTreeId();
            List<ObjectId> parentIds = commit.getParentIds();
            if (parentIds.isEmpty()) {
                diffRootTreeIds.add(new ObjectId[] { RevTree.EMPTY_TREE_ID, rightTreeId });
                continue;
            }
            for (ObjectId parentId : parentIds) {

                //() -> source.getCommit(parentId)
                Supplier<RevCommit> fn = new Supplier<RevCommit>() {
                    @Override public RevCommit get() {
                        return source.getCommit(parentId);
                    }
                };

                final @Nullable RevCommit parent = parentId.isNull() ? null
                        : Optional.fromNullable((RevCommit) rootsById.get(parentId))
                                .or(fn);

                ObjectId oldRootTreeId = parent == null ? RevTree.EMPTY_TREE_ID
                        : parent.getTreeId();
                diffRootTreeIds.add(new ObjectId[] { oldRootTreeId, rightTreeId });
            }
        }

        return diffRootTreeIds;
    }

    private void applyIndex(PackProcessor target, RefRequest req, Deduplicator deduplicator,
            ProgressListener progress) {

        progress.setDescription("Updating spatial indexes for " + req.name);
        ObjectReporter objectReport = new ObjectReporter(progress);

        // back up current progress indicator
        final Function<ProgressListener, String> defaultProgressIndicator;
        defaultProgressIndicator = progress.progressIndicator();
        // set our custom progress indicator
        progress.setProgressIndicator((p) -> objectReport.toString());

        final List<IndexDef> indexes = missingIndexes.get(req);
        checkNotNull(indexes);

        final IndexDatabase sourceStore = source.indexDatabase();
        try {

            final Stopwatch sw = Stopwatch.createStarted();
            for (IndexDef def : indexes) {
                target.putIndex(def, sourceStore, objectReport, deduplicator);
            }
            progress.complete();
            if (objectReport.total.get() > 0) {
                progress.started();
                String description = String.format("Indexes updated: %,d, repeated: %,d, time: %s",
                        objectReport.inserted(), objectReport.found(), sw.stop());
                progress.setDescription(description);
            }
        } finally {
            // restore previous progress indicator
            progress.setProgressIndicator(defaultProgressIndicator);
        }
    }

}
