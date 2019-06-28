/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.dsl;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.storage.ObjectDatabase;

import lombok.NonNull;

public class Objects extends ObjectStores {

    public Objects(Context context) {
        super(context, context.objectDatabase());
    }

    public @NonNull ObjectDatabase db() {
        return context.objectDatabase();
    }

    public @NonNull RevCommit getCommit(@NonNull ObjectId commitId) {
        return db().getCommit(commitId);
    }

    public @NonNull RevTree getTree(@NonNull ObjectId treeId) {
        return db().getTree(treeId);
    }

    public @NonNull RevFeatureType getFeatureType(@NonNull ObjectId id) {
        return db().getFeatureType(id);
    }

    public @NonNull RevFeature getFeature(@NonNull ObjectId id) {
        return db().getFeature(id);
    }

    /**
     * Determines if a commit with the given {@link ObjectId} exists in the object database.
     * 
     * @param id the id to look for
     * @return true if the object was found, false otherwise
     */
    public boolean commitExists(ObjectId id) {
        RevCommit commit = db().getIfPresent(id, RevCommit.class);
        return commit != null;
    }

    public TreeWorker head() {
        return tree(Ref.HEAD);
    }

    public TreeWorker workHead() {
        return tree(Ref.WORK_HEAD);
    }

    public TreeWorker stageHead() {
        return tree(Ref.STAGE_HEAD);
    }

}
