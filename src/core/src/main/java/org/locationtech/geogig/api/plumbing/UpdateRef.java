/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Ref;
import org.locationtech.geogig.api.hooks.Hookable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Update the object name stored in a {@link Ref} safely.
 * <p>
 * 
 */
@Hookable(name = "update-ref")
public class UpdateRef extends AbstractGeoGigOp<Optional<Ref>> {

    private String name;

    private ObjectId newValue;

    private String oldValue;

    private boolean delete;

    private String reason;

    /**
     * @param name the name of the ref to update
     * @return {@code this}
     */
    public UpdateRef setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param newValue the value to set the reference to. It can be an object id
     *        {@link ObjectId#toString() hash code} or a symbolic name such as
     *        {@code "refs/origin/master"}
     * @return {@code this}
     */
    public UpdateRef setNewValue(ObjectId newValue) {
        this.newValue = newValue;
        return this;
    }

    /**
     * @param oldValue if provided, the operation will fail if the current ref value doesn't match
     *        {@code oldValue}
     * @return {@code this}
     */
    public UpdateRef setOldValue(ObjectId oldValue) {
        this.oldValue = oldValue.toString();
        return this;
    }

    public UpdateRef setOldValue(String oldValue) {
        this.oldValue = oldValue;
        return this;
    }

    /**
     * @param delete if {@code true}, the ref will be deleted
     * @return {@code this}
     */
    public UpdateRef setDelete(boolean delete) {
        this.delete = delete;
        return this;
    }

    /**
     * @param reason if provided, the ref log will be updated with this reason message
     * @return {@code this}
     */
    // TODO: reflog not yet implemented
    public UpdateRef setReason(String reason) {
        this.reason = reason;
        return this;
    }

    /**
     * Executes the operation.
     * 
     * @return the new value of the ref
     */
    @Override
    protected Optional<Ref> _call() {
        Preconditions.checkState(name != null, "name has not been set");
        Preconditions.checkState(delete || newValue != null, "value has not been set");

        if (oldValue != null) {
            String storedValue;
            try {
                storedValue = refDatabase().getRef(name);
            } catch (IllegalArgumentException e) {
                // may be updating what used to be a symred to be a direct ref
                storedValue = refDatabase().getSymRef(name);
            }
            Preconditions.checkState(oldValue.toString().equals(storedValue), "Old value ("
                    + storedValue + ") doesn't match expected value '" + oldValue + "'");
        }

        if (delete) {
            Optional<Ref> oldRef = command(RefParse.class).setName(name).call();
            if (oldRef.isPresent()) {
                refDatabase().remove(oldRef.get().getName());
            }
            return oldRef;
        }

        refDatabase().putRef(name, newValue.toString());
        return command(RefParse.class).setName(name).call();
    }

}
