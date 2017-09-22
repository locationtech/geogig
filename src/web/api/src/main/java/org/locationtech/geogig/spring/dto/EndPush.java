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
public class EndPush extends PushResponse {

    private boolean aborted = false;

    public boolean isAborted() {
        return aborted;
    }

    public void setAborted(boolean aborted) {
        this.aborted = aborted;
    }

    @Override
    protected void encode(Writer out) {
        try {
            if (!aborted) {
                out.write("Push succeeded for address: " + getCombinedAddress());
                out.flush();
            } else {
                out.write("Push aborted for address: " + getCombinedAddress() +
                        ". The ref was changed during push.");
                out.flush();
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}
