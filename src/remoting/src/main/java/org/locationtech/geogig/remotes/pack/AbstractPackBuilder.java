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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTag;

import com.google.common.collect.Lists;

public abstract class AbstractPackBuilder implements PackBuilder {

    protected enum Status {
        IDLE, READY, PROCESS_REF
    }

    private Status status = Status.IDLE;

    protected List<RevTag> tags;

    private RefRequest currentRef;

    private List<RevCommit> currentRefCommits;

    protected LinkedHashMap<RefRequest, List<RevCommit>> missingCommits;

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
        this.tags = Lists.newArrayList(tags);
        set(Status.READY);
    }

    @Override
    public void startRefResponse(RefRequest req) {
        checkNotNull(req);
        requireAndSet(Status.READY, Status.PROCESS_REF);
        this.currentRef = req;
        this.currentRefCommits = new ArrayList<>();
    }

    @Override
    public void addCommit(RevCommit commit) {
        checkNotNull(commit);
        require(Status.PROCESS_REF);
        this.currentRefCommits.add(commit);
    }

    @Override
    public void endRefResponse() {
        require(Status.PROCESS_REF);
        Collections.reverse(currentRefCommits);
        missingCommits.put(currentRef, currentRefCommits);
        currentRef = null;
        currentRefCommits = null;
        set(Status.READY);
    }

}
