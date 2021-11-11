/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.internal;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;

@Value
@RequiredArgsConstructor
@Accessors(fluent = true)
public class NodeId {

    private @NonNull String name;

    private Object value;

    public NodeId(String name) {
        this.name = name;
        this.value = null;
    }

    @SuppressWarnings("unchecked")
    public <V> V value() {
        return (V) value;
    }

}