/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage;

import static com.google.common.base.Preconditions.checkNotNull;

import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevObject;

public class ObjectInfo<T extends RevObject> {

    private final NodeRef ref;

    private final T object;

    public ObjectInfo(NodeRef ref, T object) {
        this.ref = ref;
        this.object = object;
    }

    public NodeRef ref() {
        return ref;
    }

    public Node node() {
        return ref().getNode();
    }

    public T object() {
        return object;
    }

    public static <T extends RevObject> ObjectInfo<T> of(NodeRef ref, T obj) {
        checkNotNull(ref, "ref");
        checkNotNull(obj, "obj");

        return new ObjectInfo<T>(ref, obj);
    }
}
