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
import static com.google.common.base.Preconditions.checkState;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.remotes.pack.Pack.IndexDef;
import org.locationtech.geogig.repository.IndexInfo;

import com.google.common.collect.Lists;

import lombok.NonNull;

public abstract class AbstractPackBuilder implements PackBuilder {

    protected enum Status {
        IDLE, READY, PROCESS_REF
    }

    private Status status = Status.IDLE;

    protected List<RevTag> tags;

    private RefRequest currentRef;

    private LinkedList<RevCommit> currentRefCommits;

    private LinkedList<Pack.IndexDef> currentRefIndexes;

    protected LinkedHashMap<RefRequest, List<RevCommit>> missingCommits;

    protected LinkedHashMap<RefRequest, List<Pack.IndexDef>> missingIndexes;

    private void require(Status expected) {
        checkState(this.status == expected, "Expected status %s, but it's %s", expected,
                this.status);
    }

    private void set(Status newStatus) {
        this.status = newStatus;
    }

    private void requireAndSet(Status expected, Status newStatus) {
        require(expected);
        set(newStatus);
    }

    @Override
    public void start(Set<RevTag> tags) {
        checkNotNull(tags);
        require(Status.IDLE);
        this.missingCommits = new LinkedHashMap<>();
        this.missingIndexes = new LinkedHashMap<>();
        this.tags = Lists.newArrayList(tags);
        set(Status.READY);
    }

    @Override
    public void startRefResponse(RefRequest req) {
        checkNotNull(req);
        requireAndSet(Status.READY, Status.PROCESS_REF);
        this.currentRef = req;
        this.currentRefCommits = new LinkedList<>();
        this.currentRefIndexes = new LinkedList<>();
    }

    @Override
    public void addCommit(RevCommit commit) {
        checkNotNull(commit);
        require(Status.PROCESS_REF);
        this.currentRefCommits.addFirst(commit);
    }

    public @Override void addIndex(//@formatter:off
            @NonNull IndexInfo indexInfo, 
            @NonNull ObjectId canonicalFeatureTreeId, 
            @NonNull ObjectId oldIndexTreeId, 
            @NonNull ObjectId newIndexTreeId) {//@formatter:on

        require(Status.PROCESS_REF);

        IndexDef def = IndexDef.builder()//
                .index(indexInfo)//
                .canonical(canonicalFeatureTreeId)//
                .parentIndexTreeId(oldIndexTreeId)//
                .indexTreeId(newIndexTreeId)//
                .build();

        this.currentRefIndexes.add(def);
    }

    @Override
    public void endRefResponse() {
        require(Status.PROCESS_REF);

        missingCommits.put(currentRef, currentRefCommits);
        missingIndexes.put(currentRef, currentRefIndexes);
        currentRef = null;
        currentRefCommits = null;
        currentRefIndexes = null;
        set(Status.READY);
    }

}
