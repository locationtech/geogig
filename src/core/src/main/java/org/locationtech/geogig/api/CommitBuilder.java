/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.api;

import java.util.List;

import org.locationtech.geogig.api.plumbing.HashObject;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public final class CommitBuilder {

    private ObjectId treeId;

    private List<ObjectId> parentIds;

    private String author;

    private String authorEmail;

    private String committer;

    private String committerEmail;

    private String message;

    private long authorTimestamp;

    private long committerTimestamp;

    private int authorTimeZoneOffset;

    private int committerTimeZoneOffset;

    private Platform platform;

    public CommitBuilder() {
        this(new DefaultPlatform());
    }

    /**
     * @param platform
     */
    public CommitBuilder(Platform platform) {
        Preconditions.checkNotNull(platform);
        this.platform = platform;
        this.parentIds = Lists.newArrayListWithCapacity(2);
    }

    /**
     * @param copy the commit to initialize this builder properties with
     */
    public CommitBuilder(RevCommit copy) {
        setAuthor(copy.getAuthor().getName().orNull());
        setAuthorEmail(copy.getAuthor().getEmail().orNull());
        setCommitter(copy.getCommitter().getName().orNull());
        setCommitterEmail(copy.getCommitter().getEmail().orNull());
        setMessage(copy.getMessage());
        setParentIds(copy.getParentIds());
        setTreeId(copy.getTreeId());
        setAuthorTimestamp(copy.getAuthor().getTimestamp());
        setCommitterTimestamp(copy.getCommitter().getTimestamp());
        setAuthorTimeZoneOffset(copy.getAuthor().getTimeZoneOffset());
        setCommitterTimeZoneOffset(copy.getCommitter().getTimeZoneOffset());
    }

    /**
     * @return the treeId of the commit
     */
    public ObjectId getTreeId() {
        return treeId;
    }

    /**
     * @param treeId the treeId to set
     */
    public CommitBuilder setTreeId(ObjectId treeId) {
        this.treeId = treeId;
        return this;
    }

    /**
     * @return the parent commit {@link ObjectId ids}
     */
    public List<ObjectId> getParentIds() {
        return parentIds;
    }

    /**
     * @param parentIds the parentIds to set
     */
    @SuppressWarnings("unchecked")
    public CommitBuilder setParentIds(List<ObjectId> parentIds) {
        this.parentIds = (List<ObjectId>) (parentIds == null ? Lists.newArrayListWithCapacity(2)
                : Lists.newArrayList(parentIds));
        return this;
    }

    /**
     * @return the author
     */
    public String getAuthor() {
        return author;
    }

    /**
     * @return the author's email
     */
    public String getAuthorEmail() {
        return authorEmail;
    }

    /**
     * @param author the author to set
     */
    public CommitBuilder setAuthor(String author) {
        this.author = author;
        return this;
    }

    /**
     * @param email the author's email to set
     */
    public CommitBuilder setAuthorEmail(String email) {
        this.authorEmail = email;
        return this;
    }

    /**
     * @return the committer
     */
    public String getCommitter() {
        return committer;
    }

    /**
     * @return the committer's email
     */
    public String getCommitterEmail() {
        return committerEmail;
    }

    /**
     * @param committer the committer to set
     */
    public CommitBuilder setCommitter(String committer) {
        this.committer = committer;
        return this;
    }

    /**
     * @param email the committer's email to set
     */
    public CommitBuilder setCommitterEmail(String email) {
        this.committerEmail = email;
        return this;
    }

    /**
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * @param message the message to set
     */
    public CommitBuilder setMessage(String message) {
        this.message = message;
        return this;
    }

    /**
     * @return the author's time stamp
     */
    public long getAuthorTimestamp() {
        return authorTimestamp == 0L ? getCommitterTimestamp() : authorTimestamp;
    }

    /**
     * @return the author's time zone offset
     */
    public int getAuthorTimeZoneOffset() {
        return authorTimeZoneOffset;
    }

    /**
     * @return the committer's time stamp
     */
    public long getCommitterTimestamp() {
        return committerTimestamp == 0L ? platform.currentTimeMillis() : committerTimestamp;
    }

    /**
     * @return the committer's time zone offset
     */
    public int getCommitterTimeZoneOffset() {
        return committerTimeZoneOffset;
    }

    /**
     * @param timestamp timestamp, in UTC, of when the author made the change. Let it blank for the
     *        builder to auto-set it at {@link #build()} time
     */
    public CommitBuilder setAuthorTimestamp(long timestamp) {
        this.authorTimestamp = timestamp;
        return this;
    }

    /**
     * @param timeZoneOffset Sets the time zone offset of the author
     */
    public CommitBuilder setAuthorTimeZoneOffset(int timeZoneOffset) {
        this.authorTimeZoneOffset = timeZoneOffset;
        return this;
    }

    /**
     * @param timestamp timestamp, in UTC, of the change was committed. Let it blank for the builder
     *        to auto-set it at {@link #build()} time
     */
    public CommitBuilder setCommitterTimestamp(long timestamp) {
        this.committerTimestamp = timestamp;
        return this;
    }

    /**
     * @param timeZoneOffset Sets the time zone offset of the committer
     */
    public CommitBuilder setCommitterTimeZoneOffset(int timeZoneOffset) {
        this.committerTimeZoneOffset = timeZoneOffset;
        return this;
    }

    public RevCommit build() {
        if (treeId == null) {
            throw new IllegalStateException("No tree id set");
        }

        final ObjectId treeId = this.treeId;
        final ImmutableList<ObjectId> parentIds = ImmutableList.copyOf(this.parentIds);

        final RevPerson author = new RevPersonImpl(this.author, authorEmail, getAuthorTimestamp(),
                getAuthorTimeZoneOffset());
        final RevPerson committer = new RevPersonImpl(this.committer, committerEmail,
                getCommitterTimestamp(), getCommitterTimeZoneOffset());

        final String commitMessage = this.message == null ? "" : this.message;

        RevCommit unnnamedCommit = new RevCommitImpl(ObjectId.NULL, treeId, parentIds, author,
                committer, commitMessage);
        ObjectId commitId = new HashObject().setObject(unnnamedCommit).call();

        return new RevCommitImpl(commitId, treeId, parentIds, author, committer, commitMessage);
    }
}
