/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_3;

import static com.google.common.base.Preconditions.checkNotNull;

import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.AbstractRevObject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;

class RevTreeImpl implements RevTree {

    private final ObjectId id;

    final DataBuffer data;

    public RevTreeImpl(ObjectId id, DataBuffer dataBuffer) {
        checkNotNull(id);
        checkNotNull(dataBuffer);
        this.id = id;
        this.data = dataBuffer;
    }

    /**
     * Equality is based on id
     * 
     * @see AbstractRevObject#equals(Object)
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RevTree)) {
            return false;
        }
        return id.equals(((RevTree) o).getId());
    }

    @Override
    public ObjectId getId() {
        return id;
    }

    @Override
    public long size() {
        return RevTreeFormat.size(data);
    }

    @Override
    public int numTrees() {
        return RevTreeFormat.numChildTrees(data);
    }

    @Override
    public ImmutableList<Node> trees() {
        return RevTreeFormat.trees(data);
    }

    @Override
    public ImmutableList<Node> features() {
        return RevTreeFormat.features(data);
    }

    @Deprecated
    @Override
    public ImmutableSortedMap<Integer, Bucket> buckets() {
        return RevTreeFormat.buckets(data);
    }

    @Override
    public Iterable<Bucket> getBuckets() {
        return buckets().values();
    }
}
