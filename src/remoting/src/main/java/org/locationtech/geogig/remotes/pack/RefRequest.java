/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.pack;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;

import com.google.common.base.Optional;

/**
 * Represents a conversational protocol between two repositories to create, update, and delete
 * {@link Ref}s on each end.
 * <p>
 * A {@code RefRequest} that's part of a {@link PackRequest} is sent by one repository to a remote,
 * and the remote MUST respond with a corresponding {@code RefRequest} as part of the {@link Pack}.
 */
public class RefRequest {
    /**
     * The fully qualified name of the {@link Ref} to transfer
     */
    public final String name;

    /**
     * Version of the ref to transfer objects for. {@link Optional#absent() absent} implies to
     * delete the ref at the receiving end.
     * <p>
     * Both {@code want} and {@code have} can't be absent at the same time.
     */
    public final ObjectId want;

    /**
     * Version of the ref already present in the calling end. {@link Optional#absent() absent} means
     * the ref does not exist at the calling end.
     * <p>
     * Both {@code want} and {@code have} can't be absent at the same time.
     */
    public final Optional<ObjectId> have;

    RefRequest(String name, ObjectId want, @Nullable ObjectId have) {
        checkNotNull(name);
        checkNotNull(want);
        checkArgument(!want.isNull(), "Do not request refs pointing to the NULl id");
        this.name = name;
        this.want = want;
        this.have = Optional.fromNullable(have);
    }

    public @Override String toString() {
        return String.format("GET %s[want=%s, have=%s]", name, want, have.orNull());
    }

    public @Override int hashCode() {
        return Objects.hash(name, want, have);
    }

    public @Override boolean equals(Object o) {
        if (!(o instanceof RefRequest)) {
            return false;
        }
        RefRequest r = (RefRequest) o;
        return Objects.equals(name, r.name) && Objects.equals(want, r.want)
                && Objects.equals(have, r.have);
    }

    public static RefRequest want(Ref want, @Nullable ObjectId have) {
        checkNotNull(want);
        checkArgument(!want.getObjectId().isNull(),
                "Do not request refs pointing to the NULl id: %s", want);
        if (have != null && have.isNull()) {
            have = null;
        }
        return create(want.getName(), want.getObjectId(), have);
    }

    public static RefRequest create(String name, ObjectId wantId, @Nullable ObjectId haveId) {
        return new RefRequest(name, wantId, haveId);
    }
}