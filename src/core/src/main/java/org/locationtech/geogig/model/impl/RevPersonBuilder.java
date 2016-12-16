/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import org.eclipse.jdt.annotation.Nullable;

public class RevPersonBuilder {

    public static RevPersonImpl build(@Nullable String name, @Nullable String email, long timeStamp,
            int timeZoneOffset) {
        return new RevPersonImpl(name, email, timeStamp, timeZoneOffset);
    }

}
