/*******************************************************************************
 * Copyright (c) 2012, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.api.plumbing;

import static org.locationtech.geogig.api.RevObject.TYPE.COMMIT;
import static org.locationtech.geogig.api.RevObject.TYPE.FEATURE;
import static org.locationtech.geogig.api.RevObject.TYPE.FEATURETYPE;
import static org.locationtech.geogig.api.RevObject.TYPE.TAG;
import static org.locationtech.geogig.api.RevObject.TYPE.TREE;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevObject;

import com.google.common.base.Preconditions;
import com.google.common.hash.Funnel;
import com.google.common.hash.Hasher;

/**
 * Hashes a RevObject and returns the ObjectId.
 * 
 * @see RevObject
 * @see ObjectId#HASH_FUNCTION
 */
public class HashObject extends AbstractGeoGigOp<ObjectId> {

    @SuppressWarnings("unchecked")
    private static final Funnel<? extends RevObject>[] FUNNELS = new Funnel[RevObject.TYPE.values().length];
    static {
        FUNNELS[COMMIT.value()] = HashObjectFunnels.commitFunnel();
        FUNNELS[TREE.value()] = HashObjectFunnels.treeFunnel();
        FUNNELS[FEATURE.value()] = HashObjectFunnels.featureFunnel();
        FUNNELS[TAG.value()] = HashObjectFunnels.tagFunnel();
        FUNNELS[FEATURETYPE.value()] = HashObjectFunnels.featureTypeFunnel();
    }

    private RevObject object;

    /**
     * @param object {@link RevObject} to hash.
     * @return {@code this}
     */
    public HashObject setObject(RevObject object) {
        this.object = object;
        return this;
    }

    /**
     * Hashes a RevObject using a SHA1 hasher.
     * 
     * @return a new ObjectId created from the hash of the RevObject.
     */
    @Override
    protected  ObjectId _call() {
        Preconditions.checkState(object != null, "Object has not been set.");

        final Hasher hasher = ObjectId.HASH_FUNCTION.newHasher();
        @SuppressWarnings("unchecked")
        final Funnel<RevObject> funnel = (Funnel<RevObject>) FUNNELS[object.getType().value()];
        funnel.funnel(object, hasher);
        final byte[] rawKey = hasher.hash().asBytes();
        final ObjectId id = ObjectId.createNoClone(rawKey);

        return id;
    }

}
