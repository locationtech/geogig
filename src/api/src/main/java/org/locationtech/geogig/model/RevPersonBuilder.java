/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.model;

import org.eclipse.jdt.annotation.Nullable;

import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

public @Accessors(fluent = true) @ToString class RevPersonBuilder {

    private @Setter @Nullable String name;

    private @Setter @Nullable String email;

    private @Setter long timeStamp;

    private @Setter int timeZoneOffset;

    public RevPerson build() {
        return build(name, email, timeStamp, timeZoneOffset);
    }

    public RevPerson build(@Nullable String name, @Nullable String email, long timeStamp,
            int timeZoneOffset) {
        return RevObjectFactory.defaultInstance().createPerson(name, email, timeStamp,
                timeZoneOffset);

    }

}
