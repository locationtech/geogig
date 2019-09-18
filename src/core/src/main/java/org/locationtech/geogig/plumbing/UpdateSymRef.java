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

import java.util.Optional;

import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.RefChange;

import com.google.common.base.Preconditions;

import lombok.Getter;

/**
 * Update the object name stored in a {@link Ref} safely.
 * <p>
 * 
 * @implNote delegates to {@link UpdateRefs}
 */
@Hookable(name = "update-sym-ref")
public class UpdateSymRef extends AbstractGeoGigOp<Optional<Ref>> {

    private @Getter String name;

    private @Getter String newValue;

    private @Getter String oldValue;

    private @Getter boolean delete;

    private @Getter String reason;

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
    public UpdateSymRef setReason(String reason, Object... formatArgs) {
        this.reason = String.format(reason, formatArgs);
        return this;
    }

    /**
     * The new ref if created or updated a sym ref, or the old one if deleting it
     */
    protected @Override Optional<Ref> _call() {
        Preconditions.checkState(name != null, "name has not been set");
        Preconditions.checkState(delete || newValue != null, "value has not been set");

        if (oldValue != null) {
            Optional<Ref> curr = refDatabase().get(name).map(Ref::peel);
            String storedTarget = curr.map(Ref::getName).orElse(null);
            String storedId = curr.map(Ref::getObjectId).map(ObjectId::toString).orElse(null);
            Preconditions.checkState(oldValue.equals(storedTarget) || oldValue.equals(storedId),
                    "Old value (%s) doesn't match expected value '%s'", storedTarget, oldValue);
        }

        UpdateRefs updateCmd = command(UpdateRefs.class).setReason(reason);
        if (delete) {
            updateCmd.remove(name);
        } else {
            updateCmd.add(new SymRef(name, new Ref(newValue, ObjectId.NULL)));
        }
        RefChange change = updateCmd.call().get(0);
        return delete ? change.oldValue() : change.newValue();
    }

}
