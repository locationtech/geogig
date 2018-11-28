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

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.plumbing.HashObject;

import lombok.NonNull;

public class RevTagBuilder {

    @Deprecated
    public static RevTag create(@NonNull ObjectId id, @NonNull String name,
            @NonNull ObjectId commitId, @NonNull String message, @NonNull RevPerson tagger) {
        return RevObjectFactory.defaultInstance().createTag(id, name, commitId, message, tagger);
    }

    public static RevTag build(@NonNull String name, @NonNull ObjectId commitId,
            @NonNull String message, @NonNull RevPerson tagger) {
        ObjectId id = HashObject.hashTag(name, commitId, message, tagger);
        return RevObjectFactory.defaultInstance().createTag(id, name, commitId, message, tagger);
    }
}
