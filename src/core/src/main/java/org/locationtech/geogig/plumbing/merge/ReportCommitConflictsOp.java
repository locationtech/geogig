/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing.merge;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.DiffFeature;
import org.locationtech.geogig.plumbing.DiffTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.diff.AttributeDiff;
import org.locationtech.geogig.plumbing.diff.FeatureDiff;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.repository.impl.DepthSearch;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.DiffObjectInfo;
import org.locationtech.geogig.storage.ObjectDatabase;

import lombok.NonNull;
import lombok.Setter;

/**
 * Used for cherry pick and rebase to see if the changes from a single commit conflict with another
 * branch.
 * <p>
 * Reports conflicts between changes introduced by a given commit and the last commit of the current
 * head. That should give information about whether the specified commit can be applied safely on
 * the current branch without overwriting changes. It classifies the changes of the commit in
 * conflicting or unconflicting, so they can be applied partially
 */
public class ReportCommitConflictsOp extends AbstractGeoGigOp<MergeScenarioReport> {

    private RevCommit commit;

    private MergeScenarioConsumer consumer;

    /**
     * @param commit the commit with the changes to apply {@link RevCommit}
     */
    public ReportCommitConflictsOp setCommit(RevCommit commit) {
        this.commit = commit;
        return this;
    }

    public ReportCommitConflictsOp setConsumer(MergeScenarioConsumer consumer) {
        this.consumer = consumer;
        return this;
    }

    public ReportCommitConflictsOp setOnConflict(@NonNull Consumer<Conflict> conflictConsumer) {
        ensureEventConsumer().setConflicts(conflictConsumer);
        return this;
    }

    public ReportCommitConflictsOp setOnUnconflictedChange(
            @NonNull Consumer<DiffEntry> unconflictedConsumer) {
        ensureEventConsumer().setUnconflicted(unconflictedConsumer);
        return this;
    }

    public ReportCommitConflictsOp setOnFeatureMerged(
            @NonNull Consumer<FeatureInfo> mergedConsumer) {
        ensureEventConsumer().setMerged(mergedConsumer);
        return this;
    }

    private EventConsumer ensureEventConsumer() {
        if (consumer == null) {
            consumer = new EventConsumer();
        } else if (!(consumer instanceof EventConsumer)) {
            throw new IllegalStateException("consumer is already set");
        }
        return (EventConsumer) consumer;
    }

    private static class EventConsumer extends MergeScenarioConsumer {
        private @Setter Consumer<Conflict> conflicts = c -> {
        };

        private @Setter Consumer<DiffEntry> unconflicted = u -> {
        };

        private @Setter Consumer<FeatureInfo> merged = m -> {
        };

        public @Override void conflicted(Conflict conflict) {
            this.conflicts.accept(conflict);
        }

        public @Override void unconflicted(DiffEntry diff) {
            this.unconflicted.accept(diff);
        }

        public @Override void merged(FeatureInfo featureInfo) {
            this.merged.accept(featureInfo);
        }
    }

    protected @Override MergeScenarioReport _call() {

        Preconditions.checkArgument(consumer != null, "No consumer provided.");

        MergeScenarioReport report = new MergeScenarioReport();

        ObjectId parentCommitId = ObjectId.NULL;
        if (commit.getParentIds().size() > 0) {
            parentCommitId = commit.getParentIds().get(0);
        }

        final ObjectId parentTreeId;
        final Geogig repository = geogig();
        if (parentCommitId.isNull()) {
            parentTreeId = ObjectId.NULL;
        } else {
            parentTreeId = repository.objects().getCommit(parentCommitId).getTreeId();
        }
        // get changes
        DiffTree diffCmd = command(DiffTree.class).setOldTree(parentTreeId)
                .setNewTree(commit.getTreeId()).setReportTrees(true);

        final RevFeatureTypeCache ftCache = new RevFeatureTypeCache(objectDatabase());
        final RevTree workingTree = this.workingTree().getTree();
        try (AutoCloseableIterator<DiffObjectInfo<RevObject>> diffObjects = objectDatabase()
                .getDiffObjects(diffCmd.call(), RevObject.class)) {

            while (diffObjects.hasNext()) {
                final DiffObjectInfo<RevObject> diffEntry = diffObjects.next();
                final DiffEntry diff = diffEntry.entry();
                final java.util.Optional<RevObject> oldObject = diffEntry.oldValue();
                final java.util.Optional<RevObject> newObject = diffEntry.newValue();
                final String path = diff.path();

                final Optional<RevObject> headObj = command(RevObjectParse.class)
                        .setRefSpec(Ref.HEAD + ":" + path).call();
                switch (diff.changeType()) {
                case ADDED:
                    if (headObj.isPresent()) {
                        TYPE type = newObject.get().getType();
                        if (TYPE.TREE.equals(type)) {
                            NodeRef headVersion = command(FindTreeChild.class)
                                    .setChildPath(path).setParent(repository.commands()
                                            .resolveTree(Ref.HEAD).orElse(RevTree.EMPTY))
                                    .call().get();
                            if (!headVersion.metadataId()
                                    .equals(diff.getNewObject().metadataId())) {
                                consumer.conflicted(new Conflict(path, ObjectId.NULL,
                                        diff.getNewObject().metadataId(),
                                        headVersion.metadataId()));
                                report.addConflict(path);
                            }
                        } else {
                            if (!headObj.get().getId().equals(diff.newObjectId())) {
                                consumer.conflicted(new Conflict(path, ObjectId.NULL,
                                        diff.newObjectId(), headObj.get().getId()));
                                report.addConflict(path);
                            }
                        }
                    } else {
                        consumer.unconflicted(diff);
                        report.addUnconflicted(diff);
                    }
                    break;
                case REMOVED:
                    if (headObj.isPresent()) {
                        if (headObj.get().getId().equals(diff.oldObjectId())) {
                            consumer.unconflicted(diff);
                            report.addUnconflicted(diff);
                        } else {
                            consumer.conflicted(new Conflict(path, diff.oldObjectId(),
                                    ObjectId.NULL, headObj.get().getId()));
                            report.addConflict(path);
                        }
                    }
                    break;
                case MODIFIED:
                    if (TYPE.TREE.equals(newObject.get().getType())) {
                        // TODO:see how to do this. For now, we will pass any change as a conflicted
                        // one
                        if (!diff.isChange()) {
                            consumer.unconflicted(diff);
                            report.addUnconflicted(diff);
                        }
                        break;
                    }
                    if (!headObj.isPresent()) {
                        // git reports this as a conflict but does not mark as conflicted, just
                        // adds the missing file.
                        // We add it and consider it unconflicted
                        consumer.unconflicted(diff);
                        report.addUnconflicted(diff);
                        break;
                    }
                    final RevFeature oldFeature = (RevFeature) oldObject.get();
                    final RevFeature newFeature = (RevFeature) newObject.get();
                    final RevFeature headFeature = objectDatabase()
                            .getFeature(headObj.get().getId());

                    final NodeRef headFeatureRef = new DepthSearch(objectDatabase())
                            .find(workingTree, path).get();
                    final RevFeatureType headFeatureType = ftCache.get(headFeatureRef.metadataId());
                    List<PropertyDescriptor> descriptors = headFeatureType.descriptors();

                    FeatureDiff featureDiff = DiffFeature.compare(path, oldFeature, newFeature,
                            ftCache.get(diff.oldMetadataId()), ftCache.get(diff.newMetadataId()));

                    Iterator<Entry<PropertyDescriptor, AttributeDiff>> iterator = featureDiff
                            .getDiffs().entrySet().iterator();

                    boolean ok = true;
                    while (iterator.hasNext() && ok) {

                        Entry<PropertyDescriptor, AttributeDiff> entry = iterator.next();
                        AttributeDiff attrDiff = entry.getValue();
                        PropertyDescriptor descriptor = entry.getKey();
                        switch (attrDiff.getType()) {
                        case ADDED:
                            if (descriptors.contains(descriptor)) {
                                ok = false;
                            }
                            break;
                        case REMOVED:
                        case MODIFIED:
                            if (!descriptors.contains(descriptor)) {
                                ok = false;
                                break;
                            }
                            for (int i = 0; i < descriptors.size(); i++) {
                                if (descriptors.get(i).equals(descriptor)) {
                                    Optional<Object> value = headFeature.get(i);
                                    Optional<Object> newValue = newFeature.get(i);
                                    if (!newValue.equals(value)) { // if it's going to end up
                                                                   // setting the same value, it
                                                                   // is
                                                                   // compatible, so no need to
                                                                   // check
                                        ok = attrDiff.canBeAppliedOn(value.orElse(null));
                                        break;
                                    }
                                }
                            }
                        default:
                            // no-op
                        }
                    }
                    if (ok) {
                        consumer.unconflicted(diff);
                        report.addUnconflicted(diff);
                    } else {
                        consumer.conflicted(new Conflict(path, diff.oldObjectId(),
                                diff.newObjectId(), headObj.get().getId()));
                        report.addConflict(path);
                    }

                    break;
                }

            }
        }
        consumer.finished();

        return report;

    }

    private static class RevFeatureTypeCache {
        private ObjectDatabase db;

        private Map<ObjectId, RevFeatureType> cache = new HashMap<>();

        RevFeatureTypeCache(ObjectDatabase db) {
            this.db = db;
        }

        public RevFeatureType get(ObjectId ftId) {
            RevFeatureType type = cache.get(ftId);
            if (type == null) {
                type = db.getFeatureType(ftId);
                cache.put(ftId, type);
            }
            return type;
        }
    }
}
