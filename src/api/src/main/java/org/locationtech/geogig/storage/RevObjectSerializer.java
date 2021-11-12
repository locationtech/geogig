/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;

import lombok.NonNull;

/**
 * Defines a serialization/de-serialization mechanism for {@link RevObject} instances
 * 
 */
public interface RevObjectSerializer {

    void write(@NonNull RevObject o, @NonNull OutputStream out) throws IOException;

    RevObject read(ObjectId id, @NonNull InputStream in) throws IOException;

    RevObject read(ObjectId id, @NonNull byte[] data, int offset, int length) throws IOException;

    @NonNull
    String getDisplayName();

    /**
     * @return {@code true} if multiple {@link RevObject} instances can be written to a byte stream
     *         and then read from the resulting byte stream using this serializer.
     */
    public default boolean supportsStreaming() {
        return true;
    }
}
