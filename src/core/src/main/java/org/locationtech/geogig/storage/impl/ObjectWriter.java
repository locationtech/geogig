/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import java.io.IOException;
import java.io.OutputStream;

import org.locationtech.geogig.model.RevObject;

/**
 * Provides an interface for writing objects to a given output stream.
 */
public interface ObjectWriter<T extends RevObject> {

    /**
     * Writes the object to the given output stream. Does not close the output stream, as it doesn't
     * belong to this object. The calling code is responsible of the outputstream life cycle.
     * 
     * @param object the object to serialize
     * @param out the stream to write to
     * @throws IOException
     */
    public void write(T object, OutputStream out) throws IOException;
}
