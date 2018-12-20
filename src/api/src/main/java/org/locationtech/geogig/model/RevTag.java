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

/**
 * An immutable data structure used to permanently mark a named repository snapshot.
 * <p>
 * In the same way {@link RevCommit commits} point to a root tree, representing a snapshot of a full
 * repository dataset at a given point in time, {@code RevTag}s point to a commit in order to
 * permanently signal a snapshot of interest of a repository dataset.
 * <p>
 * For example, as the datasets in the repository evolve, tags may be used to mark production
 * versions. A tag has a name and a pointer to a commit. So a versioning scheme could be defined to
 * track snapshots of datasets, such as tags named {@code version-1.0}, {@code version-2.0}, etc.
 * <p>
 * The same way the current tip commit of a branch is referred to by a {@link Ref} object in the
 * {@code refs/heads} namespace, tags are referred to by {@link Ref}s in the {@code refs/tags}
 * namespace, and the ref values are the {@link ObjectId} of the {@code RevTag} object instead of
 * the objectId of the {@link RevCommit} object.
 * 
 * @implNote several operations are expected to resolve ref names to the commit or root tree they
 *           ultimately point to, regardless of the ref name pointing to a tag, a commit, or a tree.
 * 
 * @since 1.0
 */
public interface RevTag extends RevObject {

    /**
     * @return the name fiven for the tag object
     */
    public abstract String getName();

    /**
     * A human readable message containing the rationale for the permanent snapshot the tag
     * represents, provided by the tag {@link #getTagger() creator}.
     * 
     * @return the tag message
     */
    public abstract String getMessage();

    /**
     * @return the object representing the individual that created the tag object and the time when
     *         it was created.
     */
    public abstract RevPerson getTagger();

    /**
     * @return the {@code ObjectId} of the commit that this tag points to
     */
    public abstract ObjectId getCommitId();

    public static RevTagBuilder builder() {
        return new RevTagBuilder();
    }
}
