/* Copyright (c) 2013-2019 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 * Gabriel Roldan - pulled off TransactionRefDatabase
 */
package org.locationtech.geogig.storage;

import java.util.Optional;

import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.model.Ref;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class RefChange {

    private final @NonNull Optional<Ref> oldValue;

    private final @NonNull Optional<Ref> newValue;

    private final @NonNull String name;

    public static RefChange of(@NonNull String name, @NonNull Optional<Ref> oldValue,
            @NonNull Optional<Ref> newValue) {

        String oldValueName = oldValue.map(Ref::getName).orElse(null);
        String newValueName = newValue.map(Ref::getName).orElse(null);

        Preconditions.checkArgument(oldValueName == null || name.equals(oldValueName),
                "name: %s, oldValue name: %s", name, oldValueName);
        Preconditions.checkArgument(newValueName == null || name.equals(newValueName),
                "name: %s, newValue name: %s", name, newValueName);

        return new RefChange(oldValue, newValue, name);
    }

    public static RefChange of(@NonNull String name, Ref oldValue, Ref newValue) {
        return RefChange.of(name, Optional.ofNullable(oldValue), Optional.ofNullable(newValue));
    }

    public boolean isDelete() {
        return !newValue.isPresent();
    }

    public boolean isNew() {
        return !oldValue.isPresent() && newValue.isPresent();
    }
}