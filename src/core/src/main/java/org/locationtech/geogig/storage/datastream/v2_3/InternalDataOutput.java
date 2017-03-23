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

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

final class InternalDataOutput extends DataOutputStream {

    InternalDataOutput(int initialSize) {
        super(new InternalOutputStream(initialSize));
    }

    public void writeTo(DataOutput out) throws IOException {
        super.flush();
        InternalOutputStream buff = (InternalOutputStream) super.out;
        int size = buff.size();
        out.write(buff.internal(), 0, size);
    }

    public DataOutput reset() {
        ((InternalOutputStream) out).reset();
        return this;
    }

    public void setSize(int size) {
        ((InternalOutputStream) out).setSize(size);
    }

    static InternalDataOutput stream(int initialSize) {
        return new InternalDataOutput(initialSize);
    }
}