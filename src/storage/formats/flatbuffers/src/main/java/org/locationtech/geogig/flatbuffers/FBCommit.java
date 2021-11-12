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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.locationtech.geogig.flatbuffers.generated.v1.Commit;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevPerson;

import lombok.NonNull;

final class FBCommit extends FBRevObject<Commit> implements RevCommit {

    public FBCommit(@NonNull Commit c) {
        super(c);
    }

    public @Override TYPE getType() {
        return TYPE.COMMIT;
    }

    public @Override ObjectId getTreeId() {
        return FBAdapters.toId(getTable().treeId());
    }

    public @Override List<ObjectId> getParentIds() {
        List<ObjectId> parents = new ArrayList<>();
        int parentIdsLength = getTable().parentIdsLength();
        for (int i = 0; i < parentIdsLength; i++) {
            parents.add(FBAdapters.toId(getTable().parentIds(i)));
        }
        return parents;
    }

    public @Override Optional<ObjectId> parentN(int parentIndex) {
        int parentIdsLength = getTable().parentIdsLength();
        if (parentIdsLength > parentIndex) {
            return Optional.of(FBAdapters.toId(getTable().parentIds(parentIndex)));
        }
        return Optional.empty();
    }

    public @Override RevPerson getAuthor() {
        return new FBPerson(getTable().author());
    }

    public @Override RevPerson getCommitter() {
        return new FBPerson(getTable().committer());
    }

    public @Override String getMessage() {
        return getTable().message();
    }

    public @Override String toString() {
        return RevObjects.toString(this);
    }
}
