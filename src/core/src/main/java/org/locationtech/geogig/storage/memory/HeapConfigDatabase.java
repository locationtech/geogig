/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garret (Prominent Edge) - initial implementation
 * Gabriel Roldan (Boundless) - moved from api to core
 */
package org.locationtech.geogig.storage.memory;

import org.locationtech.geogig.storage.ConfigStore;
import org.locationtech.geogig.storage.internal.AbstractConfigDatabase;

import lombok.NonNull;

public class HeapConfigDatabase extends AbstractConfigDatabase {

    private final ConfigStore global;

    private final HeapConfigStore local;

    public HeapConfigDatabase(@NonNull ConfigStore global) {
        this(global, false);
    }

    public HeapConfigDatabase(@NonNull ConfigStore global, boolean readOnly) {
        super(readOnly);
        this.global = global;
        this.local = new HeapConfigStore();
    }

    protected @Override ConfigStore local() {
        return local;
    }

    protected @Override ConfigStore global() {
        return global;
    }
}
