/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_2;

import java.util.Map;

import static com.google.common.base.Preconditions.*;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;

import com.google.common.base.Optional;
import com.vividsolutions.jts.geom.Envelope;

class LazyNode extends Node {

    private final NodeSet set;

    private final int nodeIndex;

    private final int nameIndex;

    private final int oidIndex;

    private final int mdIdIndex;

    private final int boundsIndex;

    private final int extraDataRelOffset;

    LazyNode(final NodeSet set, int nodeIndex, int nameIndex, int oidIndex, int mdIdIndex,
            int boundsIndex, int extraDataRelOffset) {
        checkArgument(nodeIndex > -1);
        checkArgument(nameIndex > -1);
        checkArgument(oidIndex > -1);
        this.set = set;
        this.nodeIndex = nodeIndex;
        this.nameIndex = nameIndex;
        this.oidIndex = oidIndex;
        this.mdIdIndex = mdIdIndex;
        this.boundsIndex = boundsIndex;
        this.extraDataRelOffset = extraDataRelOffset;
    }

    @Override
    public TYPE getType() {
        return set.getType();
    }

    @Override
    public String getName() {
        return set.getName(nameIndex);
    }

    @Override
    public ObjectId getObjectId() {
        return set.getObjectId(oidIndex);
    }

    @Override
    public Optional<ObjectId> getMetadataId() {
        return set.getMetadataId(mdIdIndex);
    }

    @Override
    public Optional<Envelope> bounds() {
        return set.getBounds(nodeIndex, boundsIndex);
    }

    @Override
    public boolean intersects(Envelope env) {
        Optional<Envelope> bounds = bounds();
        if (bounds.isPresent()) {
            return env.intersects(bounds.get());
        }
        return false;
    }

    @Override
    public void expand(Envelope env) {
        Optional<Envelope> bounds = bounds();
        if (bounds.isPresent()) {
            env.expandToInclude(bounds.get());
        }
    }

    @Override
    public Map<String, Object> getExtraData() {
        return set.getExtraData(extraDataRelOffset);
    }
}