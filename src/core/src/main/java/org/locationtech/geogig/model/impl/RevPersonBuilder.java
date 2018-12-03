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
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevPerson;

public class RevPersonBuilder {

    public static RevPerson build(@Nullable String name, @Nullable String email, long timeStamp,
            int timeZoneOffset) {
        return RevObjectFactory.defaultInstance().createPerson(name, email, timeStamp,
                timeZoneOffset);

    }

}
