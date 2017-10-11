/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Ref;

import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;

/**
 * Represents the state of a {@link Ref} at two different points in time
 */
public class RefDiff {

    public enum Type {
        ADDED_REF, REMOVED_REF, CHANGED_REF
    }

    private @Nullable Ref oldRef;

    private @Nullable Ref newRef;

    public RefDiff(@Nullable Ref oldRef, @Nullable Ref newRef) {
        checkArgument(oldRef != null || newRef != null);
        // REVISIT: enable check once we switch to the new remoting command implementations and this
        // class is effectively immutable
        // checkArgument(oldRef != null && newRef != null ?
        // oldRef.getName().equals(newRef.getName())
        // : true);
        this.oldRef = oldRef;
        this.newRef = newRef;
    }

    public static RefDiff added(Ref ref) {
        checkNotNull(ref);
        return new RefDiff(null, ref);
    }

    public static RefDiff removed(Ref ref) {
        checkNotNull(ref);
        return new RefDiff(ref, null);
    }

    public static RefDiff updated(Ref oldRef, Ref newRef) {
        checkNotNull(oldRef);
        checkNotNull(newRef);
        return new RefDiff(oldRef, newRef);
    }

    public boolean isDelete() {
        return getType() == Type.REMOVED_REF;
    }

    public boolean isNew() {
        return getType() == Type.ADDED_REF;
    }

    public boolean isUpdate() {
        return getType() == Type.CHANGED_REF;
    }

    public Ref getOldRef() {
        return oldRef;
    }

    public void setOldRef(Ref oldRef) {
        this.oldRef = oldRef;
    }

    public Ref getNewRef() {
        return newRef;
    }

    /**
     * @deprecated to be removed once we switch to the new remoting command implementations and this
     *             class is effectively immutable
     */
    public void setNewRef(Ref newRef) {
        this.newRef = newRef;
    }

    public RefDiff.Type getType() {
        Type type = Type.CHANGED_REF;
        if (oldRef == null) {
            type = Type.ADDED_REF;
        } else if (newRef == null) {
            type = Type.REMOVED_REF;
        }
        return type;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(RefDiff.class) //
                .addValue(getType()) //
                .addValue(oldRef) //
                .addValue(newRef) //
                .toString();
    }

    public Optional<Ref> oldRef() {
        return Optional.fromNullable(oldRef);
    }

    public Optional<Ref> newRef() {
        return Optional.fromNullable(newRef);
    }
}