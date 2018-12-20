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

import java.util.ArrayList;
import java.util.List;

import org.locationtech.geogig.repository.DefaultPlatform;
import org.locationtech.geogig.repository.Platform;

import com.google.common.collect.ImmutableList;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

public final @Accessors(fluent = true) class RevCommitBuilder {

    private @Getter @Setter @NonNull ObjectId treeId;

    private @Getter @Setter List<ObjectId> parentIds = new ArrayList<>();

    private @Getter @Setter String message;

    private @Getter @Setter String committer;

    private @Getter @Setter String author;

    private @Getter @Setter String authorEmail;

    private @Getter @Setter String committerEmail;

    private @Getter @Setter Long authorTimestamp;

    private @Getter @Setter Long committerTimestamp;

    private @Getter @Setter Integer authorTimeZoneOffset;

    private @Getter @Setter Integer committerTimeZoneOffset;

    private @Setter Platform platform;

    RevCommitBuilder() {

    }

    /**
     * @param copy the commit to initialize this builder properties with
     */
    public RevCommitBuilder init(@NonNull RevCommit copy) {
        author = copy.getAuthor().getName().orNull();
        authorEmail = copy.getAuthor().getEmail().orNull();
        committer = copy.getCommitter().getName().orNull();
        committerEmail = copy.getCommitter().getEmail().orNull();
        message = copy.getMessage();
        parentIds = new ArrayList<>(copy.getParentIds());
        treeId = copy.getTreeId();
        authorTimestamp = copy.getAuthor().getTimestamp();
        committerTimestamp = copy.getCommitter().getTimestamp();
        authorTimeZoneOffset = copy.getAuthor().getTimeZoneOffset();
        committerTimeZoneOffset = copy.getCommitter().getTimeZoneOffset();
        return this;
    }

    public RevCommit build() {
        if (treeId == null) {
            throw new IllegalStateException("No tree id set");
        }

        if (platform == null) {
            platform = new DefaultPlatform();
        }
        final long now = platform.currentTimeMillis();
        final int tzOffset = platform.timeZoneOffset(now);

        long committerTs = committerTimestamp == null ? now : committerTimestamp;
        int committerOffset = committerTimeZoneOffset == null ? tzOffset : committerTimeZoneOffset;

        long authorTs = authorTimestamp == null ? committerTs : authorTimestamp;
        int authorOffset = authorTimeZoneOffset == null ? committerOffset : authorTimeZoneOffset;

        final ObjectId treeId = this.treeId;
        final ImmutableList<ObjectId> parentIds = this.parentIds == null ? ImmutableList.of()
                : ImmutableList.copyOf(this.parentIds);

        final RevPerson author = RevPerson.builder().build(this.author, authorEmail, authorTs,
                authorOffset);
        final RevPerson committer = RevPerson.builder().build(this.committer, committerEmail,
                committerTs, committerOffset);

        final String commitMessage = this.message == null ? "" : this.message;

        return build(treeId, parentIds, author, committer, commitMessage);
    }

    public RevCommit build(@NonNull ObjectId treeId, @NonNull List<ObjectId> parents,
            @NonNull RevPerson author, @NonNull RevPerson committer, @NonNull String message) {

        final ObjectId commitId = HashObjectFunnels.hashCommit(treeId, parents, author, committer,
                message);

        return RevObjectFactory.defaultInstance().createCommit(commitId, treeId,
                ImmutableList.copyOf(parents), author, committer, message);
    }
}
