/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 * 
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.dto;

import java.io.Writer;

/**
 *
 */
public class SendObject extends LegacyRepoResponse {

    private long inserted;
    private long existing;

    public long getInserted() {
        return inserted;
    }

    public SendObject setInserted(long inserted) {
        this.inserted = inserted;
        return this;
    }

    public long getExisting() {
        return existing;
    }

    public SendObject setExisting(long existing) {
        this.existing = existing;
        return this;
    }

    public long getTotal() {
        return inserted + existing;
    }
    @Override
    protected void encode(Writer out) {
        // don't need to do anything other than send back the 200 OK
    }

}
