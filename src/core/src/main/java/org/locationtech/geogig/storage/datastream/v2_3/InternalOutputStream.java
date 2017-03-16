/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_3;

import java.io.ByteArrayOutputStream;

import com.google.common.base.Preconditions;

final class InternalOutputStream extends ByteArrayOutputStream {
    public InternalOutputStream(int size) {
        super(size);
    }

    public void setSize(int size) {
        Preconditions.checkArgument(size >= 0);
        Preconditions.checkArgument(size <= super.buf.length);
        super.count = size;
    }

    public byte[] internal() {
        return super.buf;
    }
}