/* Copyright (c) 2014-2016 Boundless and others.
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
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;

import lombok.Getter;

/**
 * An annotated tag.
 * 
 */
class RevTagImpl extends AbstractRevObject implements RevTag {

    private final @Getter String name;

    private final @Getter ObjectId commitId;

    private final @Getter String message;

    private final @Getter RevPerson tagger;

    /**
     * Constructs a new {@code RevTag} with the given {@link ObjectId}, name, commit id and message.
     * 
     * @param id the {@code ObjectId} to use for this tag
     * @param name the name of the tag
     * @param commitId the {@code ObjectId} of the commit that this tag points to
     * @param message the tag message
     */
    RevTagImpl(final ObjectId id, final String name, final ObjectId commitId, final String message,
            RevPerson tagger) {
        super(id);
        this.name = name;
        this.commitId = commitId;
        this.message = message;
        this.tagger = tagger;
    }

    public @Override TYPE getType() {
        return TYPE.TAG;
    }
    
    public @Override String toString() {
        return RevObjects.toString(this);
    }
}
