/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.flatbuffers;

import org.locationtech.geogig.flatbuffers.generated.v1.Tag;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.RevTag;

import lombok.NonNull;

final class FBTag extends FBRevObject<Tag> implements RevTag {

    public FBTag(@NonNull Tag t) {
        super(t);
    }

    public @Override String toString() {
        return RevObjects.toString(this);
    }

    public @Override TYPE getType() {
        return TYPE.TAG;
    }

    public @Override String getName() {
        return getTable().name();
    }

    public @Override String getMessage() {
        return getTable().message();
    }

    public @Override RevPerson getTagger() {
        return new FBPerson(getTable().tagger());
    }

    public @Override ObjectId getCommitId() {
        return FBAdapters.toId(getTable().commitId());
    }

}
