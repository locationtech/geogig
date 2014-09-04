/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.bdbje;

import java.io.ByteArrayOutputStream;

final class InternalByteArrayOutputStream extends ByteArrayOutputStream {

    public InternalByteArrayOutputStream(int initialBuffSize) {
        super(initialBuffSize);
    }

    public byte[] bytes() {
        return super.buf;
    }

    public int size() {
        return super.count;
    }
}