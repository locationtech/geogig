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

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.jts.geom.Envelope;

import com.google.common.base.Optional;

import lombok.AllArgsConstructor;
import lombok.NonNull;

final @AllArgsConstructor class FBBucket extends Bucket {

    private final @NonNull org.locationtech.geogig.flatbuffers.generated.v1.Bucket bucket;

    public @Override int getIndex() {
        return bucket.index();
    }

    public @Override ObjectId getObjectId() {
        return FBAdapters.toId(bucket.treeId());
    }

    public @Override boolean intersects(Envelope env) {
        return FBAdapters.intersects(bucket.bounds(), env);
    }

    public @Override void expand(Envelope env) {
        FBAdapters.expandEnv(env, bucket.bounds());
    }

    public @Override Optional<Envelope> bounds() {
        return FBAdapters.toEnvelopeOpt(bucket.bounds());
    }

}
