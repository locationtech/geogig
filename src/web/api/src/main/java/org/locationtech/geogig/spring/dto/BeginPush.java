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
public class BeginPush extends PushResponse {

    @Override
    protected void encode(Writer out) {
        try {
            out.write("Push began for address: " + getCombinedAddress());
            out.flush();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
