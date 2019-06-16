/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import java.util.List;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.storage.GraphDatabase;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SynchronizedGraphDatabase implements GraphDatabase {
    private final @NonNull GraphDatabase delegate;

    public void open() {
        synchronized (delegate) {
            delegate.open();
        }
    }

    public void close() {
        synchronized (delegate) {
            delegate.close();
        }
    }

    @Override
    public boolean isReadOnly() {
        return delegate.isReadOnly();
    }

    public boolean isOpen() {
        return delegate.isOpen();
    }

    public boolean exists(final ObjectId commitId) {
        synchronized (delegate) {
            return delegate.exists(commitId);
        }
    }

    public List<ObjectId> getParents(ObjectId commitId) throws IllegalArgumentException {
        synchronized (delegate) {
            return delegate.getParents(commitId);
        }
    }

    public List<ObjectId> getChildren(ObjectId commitId) throws IllegalArgumentException {
        synchronized (delegate) {
            return delegate.getChildren(commitId);
        }
    }

    public boolean put(ObjectId commitId, List<ObjectId> parentIds) {
        synchronized (delegate) {
            return delegate.put(commitId, parentIds);
        }
    }

    public void map(ObjectId mapped, ObjectId original) {
        synchronized (delegate) {
            delegate.map(mapped, original);
        }
    }

    public ObjectId getMapping(ObjectId commitId) {
        synchronized (delegate) {
            return delegate.getMapping(commitId);
        }
    }

    public int getDepth(final ObjectId commitId) {
        synchronized (delegate) {
            return delegate.getDepth(commitId);
        }
    }

    public void setProperty(ObjectId commitId, String propertyName, String propertyValue) {
        synchronized (delegate) {
            delegate.setProperty(commitId, propertyName, propertyValue);
        }
    }

    public void truncate() {
        synchronized (delegate) {
            delegate.truncate();
        }
    }

    public GraphNode getNode(ObjectId id) {
        synchronized (delegate) {
            return delegate.getNode(id);
        }
    }
}
