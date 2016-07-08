/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.api.plumbing.merge;

import static com.google.common.base.Preconditions.checkNotNull;

import org.locationtech.geogig.api.ObjectId;

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
 */
public final class Conflict {

    private final ObjectId ancestor;

    private final ObjectId theirs;

    private final ObjectId ours;

    private final String path;

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

    public ObjectId getAncestor() {
        return ancestor;
    }

    public ObjectId getOurs() {
        return ours;
    }

    public ObjectId getTheirs() {
        return theirs;
    }

    public String getPath() {
        return path;
    }

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

    public int hashCode() {
        return Objects.hashCode(ancestor, theirs, ours, path);
    }

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
