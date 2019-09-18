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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.locationtech.geogig.hooks.Hookable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;
import org.locationtech.geogig.storage.RefChange;
import org.locationtech.geogig.storage.RefDatabase;

import lombok.Getter;
import lombok.NonNull;

/**
 * Atomically update multiple refs
 */
@Hookable(name = "update-ref")
public class UpdateRefs extends AbstractGeoGigOp<List<RefChange>> {

    private Map<String, Ref> newAndUpdated = new TreeMap<>();

    private Set<String> deletes = new HashSet<>();

    private @Getter @NonNull Optional<String> reason = Optional.empty();

    public UpdateRefs add(@NonNull String name, @NonNull ObjectId target) {
        return add(new Ref(name, target));
    }

    public UpdateRefs add(@NonNull Ref ref) {
        deletes.remove(ref.getName());
        newAndUpdated.put(ref.getName(), ref);
        return this;
    }

    public UpdateRefs addAll(@NonNull Iterable<Ref> refs) {
        refs.forEach(this::add);
        return this;
    }

    public UpdateRefs remove(@NonNull Ref ref) {
        remove(ref.getName());
        return this;
    }

    public UpdateRefs remove(@NonNull String name) {
        newAndUpdated.remove(name);
        deletes.add(name);
        return this;
    }

    /**
     * @param reason if provided, the ref log will be updated with this reason message
     * @return {@code this}
     */
    public UpdateRefs setReason(String reason, Object... formatArgs) {
        this.reason = Optional.ofNullable(String.format(reason, formatArgs));
        return this;
    }

    /**
     * @return the value of the new refs, deleted refs have {@link ObjectId#NULL}
     *         {@link Ref#getObjectId() value}
     */
    protected @Override List<RefChange> _call() {
        if (reason == null) {
            List<String> names = new ArrayList<>(this.deletes);
            names.addAll(this.newAndUpdated.keySet());
            throw new IllegalStateException("Updating "
                    + names.stream().collect(Collectors.joining(", ")) + " without reason given");
        }
        RefDatabase refDatabase = refDatabase();
        try {
            refDatabase.lock();
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
        final List<RefChange> retval = new ArrayList<>();
        try {
            if (!this.newAndUpdated.isEmpty()) {
                retval.addAll(refDatabase.putAll(this.newAndUpdated.values()));
            }
            if (!this.deletes.isEmpty()) {
                retval.addAll(refDatabase.delete(this.deletes));
            }
        } finally {
            refDatabase.unlock();
        }
        return retval;
    }
}
