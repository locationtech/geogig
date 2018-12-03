/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - pulled off from Node's inner class
 */
package org.locationtech.geogig.model.impl;

import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.jts.geom.Envelope;

final class TreeNode extends BaseNodeImpl {

    public TreeNode(String name, ObjectId oid, ObjectId mdid, @Nullable Envelope env,
            @Nullable Map<String, Object> extraData) {
        super(name, oid, mdid, env, extraData);
    }

    public final @Override TYPE getType() {
        return TYPE.TREE;
    }

}