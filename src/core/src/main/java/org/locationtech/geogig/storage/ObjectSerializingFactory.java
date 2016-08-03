/* Copyright (c) 2013-2014 Boundless and others.
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

/**
 * The ObjectSerializingFactory is used to create instances of the various writers and readers used
 * to work with the serialized forms of various repository elements.
 * 
 */
public interface ObjectSerializingFactory {

    void write(RevObject o, OutputStream out) throws IOException;

    RevObject read(ObjectId id, InputStream in) throws IOException;
}
