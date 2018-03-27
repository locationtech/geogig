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

import java.io.ByteArrayInputStream;

import com.google.common.base.Preconditions;

final class InternalInputStream extends ByteArrayInputStream {
    public InternalInputStream(byte[] buff) {
        super(buff);
    }

    public int getPosition() {
        return this.pos;
    }

    public void setPosition(int value) {
        Preconditions.checkArgument(value >= 0 && value < count);
        this.pos = value;
    }
}