/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Update the object name stored in a {@link Ref} safely.
 * <p>
 * 
 */
@Hookable(name = "update-sym-ref")
public class UpdateSymRef extends AbstractGeoGigOp<Optional<Ref>> {

    private String name;

    private String newValue;

    private String oldValue;

    private boolean delete;

    @SuppressWarnings("unused")
    private String reason;

    /**
     * @param name the name of the ref to update
     * @return {@code this}
     */
    public UpdateSymRef setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param newValue the value to set the reference to. It can be an object id
     *        {@link ObjectId#toString() hash code} or a symbolic name such as
     *        {@code "refs/origin/master"}
     * @return {@code this}
     */
    public UpdateSymRef setNewValue(String newValue) {
        this.newValue = newValue;
        return this;
    }

    /**
     * @param oldValue if provided, the operation will fail if the current ref value doesn't match
     *        {@code oldValue}
     * @return {@code this}
     */
    public UpdateSymRef setOldValue(String oldValue) {
        this.oldValue = oldValue;
        return this;
    }

    /**
     * @param delete if {@code true}, the ref will be deleted
     * @return {@code this}
     */
    public UpdateSymRef setDelete(boolean delete) {
        this.delete = delete;
        return this;
    }

    /**
     * @param reason if provided, the ref log will be updated with this reason message
     * @return {@code this}
     */
    // TODO: reflog not yet implemented
    public UpdateSymRef setReason(String reason) {
        this.reason = reason;
        return this;
    }

    /**
     * The new ref if created or updated a sym ref, or the old one if deleting it
     */
    @Override
    protected Optional<Ref> _call() {
        Preconditions.checkState(name != null, "name has not been set");
        Preconditions.checkState(delete || newValue != null, "value has not been set");

        if (oldValue != null) {
            String storedValue;
            try {
                storedValue = refDatabase().getSymRef(name);
            } catch (IllegalArgumentException e) {
                // may be updating what used to be a direct ref to be a symbolic ref
                storedValue = refDatabase().getRef(name);
            }
            Preconditions.checkState(oldValue.equals(storedValue), "Old value (" + storedValue
                    + ") doesn't match expected value '" + oldValue + "'");
        }

        if (delete) {
            Optional<Ref> oldRef = command(RefParse.class).setName(name).call();
            if (oldRef.isPresent()) {
                refDatabase().remove(name);
            }
            return oldRef;
        }

        refDatabase().putSymRef(name, newValue);
        Optional<Ref> ref = command(RefParse.class).setName(name).call();
        return ref;
    }

}
