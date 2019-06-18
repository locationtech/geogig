/* Copyright (c) 2019 Gabriel Roldan.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage.decorator;

import java.util.List;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.storage.GraphDatabase;

import lombok.RequiredArgsConstructor;

public @RequiredArgsConstructor class ForwardingGraphDatabase implements GraphDatabase {

    protected final GraphDatabase actual;

    public @Override void open() {
        actual.open();
    }

    public @Override void close() {
        actual.close();
    }

    public @Override boolean isOpen() {
        return actual.isOpen();
    }

    public @Override boolean isReadOnly() {
        return actual.isReadOnly();
    }

    public @Override void checkWritable() {
        actual.checkWritable();
    }

    public @Override void checkOpen() {
        actual.checkOpen();
    }

    public @Override boolean exists(ObjectId commitId) {
        return actual.exists(commitId);
    }

    public @Override List<ObjectId> getParents(ObjectId commitId) throws IllegalArgumentException {
        return actual.getParents(commitId);
    }

    public @Override List<ObjectId> getChildren(ObjectId commitId) throws IllegalArgumentException {
        return actual.getChildren(commitId);
    }

    public @Override boolean put(ObjectId commitId, List<ObjectId> parentIds) {
        return actual.put(commitId, parentIds);
    }

    public @Override void map(ObjectId mapped, ObjectId original) {
        actual.map(mapped, original);
    }

    public @Override ObjectId getMapping(ObjectId commitId) {
        return actual.getMapping(commitId);
    }

    public @Override int getDepth(ObjectId commitId) {
        return actual.getDepth(commitId);
    }

    public @Override void setProperty(ObjectId commitId, String propertyName,
            String propertyValue) {
        actual.setProperty(commitId, propertyName, propertyValue);
    }

    public @Override GraphNode getNode(ObjectId id) {
        return actual.getNode(id);
    }

    public @Override void truncate() {
        actual.truncate();
    }
}
