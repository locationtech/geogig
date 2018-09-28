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

import static com.google.common.base.Preconditions.checkState;
import static org.locationtech.geogig.model.Ref.HEAD;
import static org.locationtech.geogig.model.Ref.STAGE_HEAD;
import static org.locationtech.geogig.model.Ref.WORK_HEAD;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.storage.RefDatabase;

import com.google.common.base.Optional;

import lombok.Getter;

/**
 * Update the object name stored in a {@link Ref} safely.
 * <p>
 * 
 */
@Hookable(name = "update-ref")
public class UpdateRef extends AbstractGeoGigOp<Optional<Ref>> {

    private @Getter String name;

    private @Getter ObjectId newValue;

    private @Getter String oldValue;

    private @Getter boolean delete;

    private @Getter String reason;

    private boolean verifyValue = true;

    /**
     * @param name the name of the ref to update
     * @return {@code this}
     */
    public UpdateRef setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * @param newValue the value to set the reference to, {@code null} valid only if
     *        {@link #setDelete delete} is {@code true}
     * @return {@code this}
     */
    public UpdateRef setNewValue(@Nullable ObjectId newValue) {
        this.newValue = newValue;
        return this;
    }

    /**
     * @param oldValue if provided, the operation will fail if the current ref value doesn't match
     *        {@code oldValue}
     * @return {@code this}
     */
    public UpdateRef setOldValue(@Nullable ObjectId oldValue) {
        this.oldValue = oldValue == null ? null : oldValue.toString();
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

    public UpdateRef setCheckObjectExists(boolean verifyValue) {
        this.verifyValue = verifyValue;
        return this;
    }

    /**
     * Executes the operation.
     * 
     * @return the new value of the ref
     */
    @Override
    protected Optional<Ref> _call() {
        final String name = this.name;
        final boolean delete = this.delete;
        final ObjectId newValue = this.newValue;
        checkState(name != null, "name has not been set");
        checkState(delete || newValue != null, "value has not been set");

        RefDatabase refDatabase = refDatabase();
        if (oldValue != null) {
            String storedValue;
            try {
                storedValue = refDatabase.getRef(name);
            } catch (IllegalArgumentException e) {
                // may be updating what used to be a symref to be a direct ref
                storedValue = refDatabase.getSymRef(name);
            }
            checkState(storedValue == null || oldValue.equals(storedValue), "Old value ("
                    + storedValue + ") doesn't match expected value '" + oldValue + "'");
        }

        if (delete) {
            Optional<Ref> oldRef = command(RefParse.class).setName(name).call();
            if (oldRef.isPresent()) {
                refDatabase.remove(oldRef.get().getName());
            }
            return oldRef;
        }

        checkState(!verifyValue || newValue.isNull() || objectDatabase().exists(newValue),
                "Tried to update Ref %s to an object that doesn't exist: %s", name, newValue);

        // if not changing a head
        if (!HEAD.equals(name) && !WORK_HEAD.equals(name) && !STAGE_HEAD.equals(name)) {
            final @Nullable String currentHeadTarget;
            try {
                currentHeadTarget = refDatabase.getSymRef(HEAD);
                if (name.equals(currentHeadTarget)) {// and updating the current branch
                    // and the working tree and staging are are clean...
                    boolean workingTreeClean = workingTree().isClean();
                    boolean stagingAreaClean = stagingArea().isClean();
                    if (workingTreeClean && stagingAreaClean) {
                        refDatabase.putSymRef(WORK_HEAD, name);
                        refDatabase.putSymRef(STAGE_HEAD, name);
                    }
                }
            } catch (IllegalArgumentException headIsDettached) {
                // HEAD is in a dettached state
            }
        }

        refDatabase.putRef(name, newValue.toString());
        Optional<Ref> newRef = command(RefParse.class).setName(name).call();
        checkState(newRef.isPresent());

        return newRef;
    }
}
