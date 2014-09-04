/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Justin Deoliveira (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.sqlite;

import org.locationtech.geogig.api.ObjectId;

import com.google.common.base.Function;

/**
 * Function to convert string to object id.
 * 
 * @author Justin Deoliveira, Boundless
 * 
 */
public class StringToObjectId implements Function<String, ObjectId> {

    public static StringToObjectId INSTANCE = new StringToObjectId();

    @Override
    public ObjectId apply(String str) {
        return ObjectId.valueOf(str);
    }

}
