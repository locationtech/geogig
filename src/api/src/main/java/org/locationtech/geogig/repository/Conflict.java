/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import static com.google.common.base.Preconditions.checkNotNull;

import org.locationtech.geogig.model.ObjectId;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/**
 * A class to store a merge conflict. It stores the information needed to solve the conflict, saving
 * the object id's that point to the common ancestor and both versions of a given geogig element
 * that are to be merged.
 * 
 * A {@link ObjectId#NULL null} ObjectId indicates that, for the corresponding version, the element
 * did not exist
 * 
 * @since 1.0
 */
public final class Conflict {

    private final ObjectId ancestor;

    private final ObjectId theirs;

    private final ObjectId ours;

    private final String path;

    /**
     * Create a new {@code Conflict} with the provided parameters.
     * 
     * @param path the feature path that is conflicted
     * @param ancestor the {@code ObjectId} of the ancestor feature
     * @param ours the {@code ObjectId} of the feature on the 'ours' side of the merge
     * @param theirs the {@code ObjectId} of the feature on the 'theirs' side of the merge
     */
    public Conflict(String path, ObjectId ancestor, ObjectId ours, ObjectId theirs) {
        checkNotNull(path, "path");
        checkNotNull(ancestor, "ancestor");
        checkNotNull(ours, "ours");
        checkNotNull(theirs, "theirs");

        this.path = path;
        this.ancestor = ancestor;
        this.ours = ours;
        this.theirs = theirs;
    }

    /**
     * @return the {@code ObjectId} of the ancestor feature
     */
    public ObjectId getAncestor() {
        return ancestor;
    }

    /**
     * @return the {@code ObjectId} of the feature on the 'ours' side of the merge
     */
    public ObjectId getOurs() {
        return ours;
    }

    /**
     * @return the {@code ObjectId} of the feature on the 'theirs' side of the merge
     */
    public ObjectId getTheirs() {
        return theirs;
    }

    /**
     * @return the path of the conflicted feature
     */
    public String getPath() {
        return path;
    }

    /**
     * Determines if this conflict is the same as another.
     * 
     * @param x the other object
     */
    public boolean equals(Object x) {
        if (x instanceof Conflict) {
            Conflict that = (Conflict) x;
            return Objects.equal(this.ancestor, that.ancestor)
                    && Objects.equal(this.theirs, that.theirs)
                    && Objects.equal(this.ours, that.ours) && Objects.equal(this.path, that.path);
        } else {
            return false;
        }
    }

    /**
     * Generates a hash code for the conflict.
     */
    public int hashCode() {
        return Objects.hashCode(ancestor, theirs, ours, path);
    }

    /**
     * @return the Conflict represented as a readable string.
     */
    public String toString() {
        return path + "\t" + ancestor.toString() + "\t" + ours.toString() + "\t"
                + theirs.toString();
    }

    /**
     * @deprecated at 1.0-RC3. Conflict serialization shall be specific to the ConflictsDatabase
     *             implementation
     */
    @Deprecated
    public static Conflict valueOf(String line) {
        String[] tokens = line.split("\t");
        Preconditions.checkArgument(tokens.length == 4, "wrong conflict definitions: %s", line);
        String path = tokens[0];
        ObjectId ancestor = ObjectId.valueOf(tokens[1]);
        ObjectId ours = ObjectId.valueOf(tokens[2]);
        ObjectId theirs = ObjectId.valueOf(tokens[3]);
        return new Conflict(path, ancestor, ours, theirs);
    }

}
