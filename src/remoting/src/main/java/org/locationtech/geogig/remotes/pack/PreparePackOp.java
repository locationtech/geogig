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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindChangedTrees;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.IndexDatabase;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;

/**
 * Takes a {@link PackRequest} and a {@link PackBuilder} to be used to create a {@link Pack}.
 */
public class PreparePackOp extends AbstractGeoGigOp<Pack> {

    private PackRequest request;

    private PackBuilder builder;

    public PreparePackOp setRequest(PackRequest request) {
        this.request = request;
        return this;
    }

    public PackRequest getRequest() {
        return request;
    }

    public PreparePackOp setPackBuilder(PackBuilder builder) {
        this.builder = builder;
        return this;
    }

    public PackBuilder getBuilder() {
        return builder;
    }

    protected @Override Pack _call() {
        final PackRequest request = getRequest();
        final PackBuilder builder = getBuilder();
        checkState(request != null, "no request provided");
        checkState(builder != null, "no pack builder provided");

        final List<RefRequest> tagRequests = resolveTagRequests();
        final List<RefRequest> refs = resolveRefRequests();
        final Set<RevTag> tags = resolveWantTags(tagRequests);

        builder.start(tags);

        // commit ids of tips present on the remote end
        Set<ObjectId> have;
        // commit ids of tips not present on the remote end, and present on the local repo
        Set<ObjectId> want;

        boolean isTags = true;
        have = resolveHaveCommits(tagRequests, isTags);
        want = resolveWantCommits(tagRequests, isTags);

        isTags = false;
        have.addAll(resolveHaveCommits(refs, isTags));
        want.addAll(resolveWantCommits(refs, isTags));

        Iterable<RefRequest> allrequests = Iterables.concat(tagRequests, refs);
        processRequests(allrequests, want, have, builder);

        Pack pack = builder.build();
        return pack;
    }

    private Set<ObjectId> resolveWantCommits(List<RefRequest> refs, boolean isTags) {

        // (o) -> o.want
        Function<RefRequest, ObjectId> fn = new Function<RefRequest, ObjectId>() {
            public @Override ObjectId apply(RefRequest o) {
                return o.want;
            }
        };

        return resolveHeadCommits(refs, isTags, x -> true, fn);
    }

    private Set<ObjectId> resolveHaveCommits(List<RefRequest> refs, boolean isTags) {
        return resolveHeadCommits(refs, isTags, r -> r.have.isPresent(), o -> o.have.get());
    }

    private Set<RevTag> resolveWantTags(List<RefRequest> tagRequests) {
        Iterable<ObjectId> ids = transform(tagRequests, r -> r.want);

        Iterator<RevTag> tags = objectDatabase().getAll(ids, NOOP_LISTENER, RevTag.class);

        return Sets.newHashSet(tags);
    }

    private Set<ObjectId> resolveHeadCommits(List<RefRequest> refs, boolean isTags,
            Predicate<? super RefRequest> filter,
            Function<? super RefRequest, ? extends ObjectId> function) {
        List<ObjectId> ids = refs.stream().filter(filter).map(function)
                .collect(Collectors.toList());
        if (isTags) {
            Iterator<RevTag> tags = objectDatabase().getAll(ids, NOOP_LISTENER, RevTag.class);
            ids = newArrayList(Iterators.transform(tags, RevTag::getCommitId));
        }
        return Sets.newHashSet(ids);
    }

    private List<RefRequest> resolveTagRequests() {

        final PackRequest req = this.request;

        return newArrayList(filter(req.getRefs(), r -> r.name.startsWith(Ref.TAGS_PREFIX)));
    }

    private List<RefRequest> resolveRefRequests() {
        final PackRequest req = this.request;
        return newArrayList(filter(req.getRefs(), r -> !r.name.startsWith(Ref.TAGS_PREFIX)));
    }

    private void processRequests(//
            Iterable<RefRequest> allrefs, //
            Set<ObjectId> want, //
            Set<ObjectId> have, //
            PackBuilder builder//
    ) {

        Repository local = repository();
        final ProgressListener progress = getProgressListener();
        java.util.function.Function<ProgressListener, String> oldIndicator = progress
                .progressIndicator();

        progress.setProgressIndicator(
                (p) -> String.format("Resolving missing commits... %,d", (int) p.getProgress()));
        progress.started();

        Set<RevCommit> visited = new HashSet<>();

        for (RefRequest req : allrefs) {
            builder.startRefResponse(req);
            final String refName = req.name;
            checkArgument(!req.want.isNull(), "Requested NULL tip for ref %s", refName);

            ObjectId wantCommit = req.want;
            ObjectId haveCommit = req.have.orElse(ObjectId.NULL);
            Iterator<RevCommit> branchCommits;
            if (wantCommit.equals(haveCommit)) {
                branchCommits = Collections.emptyIterator();
            } else {
                if (refName.startsWith(Ref.TAGS_PREFIX)) {
                    wantCommit = local.context().objectDatabase().getTag(wantCommit).getCommitId();
                    if (!haveCommit.isNull()) {
                        haveCommit = local.context().objectDatabase().getTag(haveCommit)
                                .getCommitId();
                    }
                }

                branchCommits = local.command(LogOp.class)//
                        .setTopoOrder(true)//
                        .setUntil(wantCommit)//
                        .setSince(haveCommit.isNull() ? null : haveCommit)//
                        .call();
            }
            int count = 0;
            while (branchCommits.hasNext()) {
                RevCommit commit = branchCommits.next();
                if (visited.add(commit)) {
                    builder.addCommit(commit);
                    if (request.isSyncIndexes()) {
                        addIndexes(builder, local, commit);
                    }
                    progress.setProgress(++count);
                }
            }

            builder.endRefResponse();
        }

        progress.complete();
        progress.setProgressIndicator(oldIndicator);
    }

    private Map<String, IndexInfo> indexInfosByFeatureTreeName;

    private void addIndexes(PackBuilder builder, Repository local, RevCommit commit) {
        if (indexInfosByFeatureTreeName != null && indexInfosByFeatureTreeName.isEmpty()) {
            return;
        }

        final IndexDatabase indexdb = local.context().indexDatabase();
        indexInfosByFeatureTreeName = indexdb.getIndexInfos().stream()
                .collect(Collectors.toMap(i -> i.getTreeName(), i -> i));
        if (indexInfosByFeatureTreeName.isEmpty()) {
            return;
        }

        final List<ObjectId> parents = commit.getParentIds().isEmpty()
                ? Collections.singletonList(ObjectId.NULL)
                : commit.getParentIds();

        for (ObjectId parentId : parents) {
            List<DiffEntry> changedTrees = local.command(FindChangedTrees.class)
                    .setOldTreeIsh(parentId).setNewTreeIsh(commit.getId()).call();

            for (DiffEntry treeDiff : changedTrees) {
                final String treePath = treeDiff.path();
                final IndexInfo indexInfo = indexInfosByFeatureTreeName.get(treePath);
                if (indexInfo == null) {
                    continue;
                }
                final ObjectId oldCanonical = treeDiff.oldObjectId().isNull()
                        ? RevTree.EMPTY_TREE_ID
                        : treeDiff.oldObjectId();
                final ObjectId newCanonical = treeDiff.newObjectId().isNull()
                        ? RevTree.EMPTY_TREE_ID
                        : treeDiff.newObjectId();

                ObjectId oldIndexTreeId = indexdb.resolveIndexedTree(indexInfo, oldCanonical)
                        .orElse(RevTree.EMPTY_TREE_ID);
                ObjectId newIndexTreeId = indexdb.resolveIndexedTree(indexInfo, newCanonical)
                        .orElse(RevTree.EMPTY_TREE_ID);

                builder.addIndex(indexInfo, newCanonical, oldIndexTreeId, newIndexTreeId);

            }
        }
    }
}
