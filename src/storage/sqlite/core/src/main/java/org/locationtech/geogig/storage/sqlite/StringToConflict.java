/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.sqlite;

import org.locationtech.geogig.api.plumbing.merge.Conflict;

import com.google.common.base.Function;

/**
 * Function to convert string to conflict.
 * 
 * @author Justin Deoliveira, Boundless
 * 
 */
public class StringToConflict implements Function<String, Conflict> {

    public static final StringToConflict INSTANCE = new StringToConflict();

    @Override
    public Conflict apply(String str) {
        return Conflict.valueOf(str);
    }

}
