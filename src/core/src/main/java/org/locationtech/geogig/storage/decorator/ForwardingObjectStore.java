/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.decorator;

import java.util.Iterator;
import java.util.List;

import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ObjectInfo;
import org.locationtech.geogig.storage.ObjectStore;

public class ForwardingObjectStore implements ObjectStore {

    protected final ObjectStore actual;

    public ForwardingObjectStore(final ObjectStore odb) {
        this.actual = odb;
    }

    protected ObjectStore subject() {
        return actual;
    }

    public @Override void open() {
        actual.open();
    }

    public @Override boolean isOpen() {
        return actual.isOpen();
    }

    public @Override void close() {
        actual.close();
    }

    public @Override boolean isReadOnly() {
        return actual.isReadOnly();
    }

    public @Override boolean exists(ObjectId id) {
        checkOpen();
        return actual.exists(id);
    }

    public @Override List<ObjectId> lookUp(String partialId) {
        checkOpen();
        return actual.lookUp(partialId);
    }

    public @Override RevObject get(ObjectId id) throws IllegalArgumentException {
        checkOpen();
        return actual.get(id);
    }

    public @Override <T extends RevObject> T get(ObjectId id, Class<T> type)
            throws IllegalArgumentException {
        checkOpen();
        return actual.get(id, type);
    }

    public @Override RevObject getIfPresent(ObjectId id) {
        checkOpen();
        return actual.getIfPresent(id);
    }

    public @Override <T extends RevObject> T getIfPresent(ObjectId id, Class<T> type)
            throws IllegalArgumentException {
        checkOpen();
        return actual.getIfPresent(id, type);
    }

    public @Override RevTree getTree(ObjectId id) {
        checkOpen();
        return actual.getTree(id);
    }

    public @Override RevFeature getFeature(ObjectId id) {
        checkOpen();
        return actual.getFeature(id);
    }

    public @Override RevFeatureType getFeatureType(ObjectId id) {
        checkOpen();
        return actual.getFeatureType(id);
    }

    public @Override RevCommit getCommit(ObjectId id) {
        checkOpen();
        return actual.getCommit(id);
    }

    public @Override RevTag getTag(ObjectId id) {
        checkOpen();
        return actual.getTag(id);
    }

    public @Override boolean put(RevObject object) {
        checkWritable();
        return actual.put(object);
    }

    public @Override void delete(ObjectId objectId) {
        checkWritable();
        actual.delete(objectId);
    }

    public @Override Iterator<RevObject> getAll(Iterable<ObjectId> ids) {
        checkOpen();
        return actual.getAll(ids);
    }

    public @Override Iterator<RevObject> getAll(Iterable<ObjectId> ids, BulkOpListener listener) {
        checkOpen();
        return actual.getAll(ids, listener);
    }

    public @Override <T extends RevObject> Iterator<T> getAll(Iterable<ObjectId> ids,
            BulkOpListener listener, Class<T> type) {
        checkOpen();
        return actual.getAll(ids, listener, type);
    }

    public @Override void putAll(Iterator<? extends RevObject> objects) {
        checkWritable();
        actual.putAll(objects);
    }

    public @Override void putAll(Iterator<? extends RevObject> objects, BulkOpListener listener) {
        checkWritable();
        actual.putAll(objects, listener);
    }

    public @Override void deleteAll(Iterator<ObjectId> ids) {
        checkWritable();
        actual.deleteAll(ids);
    }

    public @Override void deleteAll(Iterator<ObjectId> ids, BulkOpListener listener) {
        checkWritable();
        actual.deleteAll(ids, listener);
    }

    public @Override String toString() {
        return String.format("%s[%s]", getClass().getSimpleName(), actual);
    }

    public @Override <T extends RevObject> AutoCloseableIterator<ObjectInfo<T>> getObjects(
            Iterator<NodeRef> refs, BulkOpListener listener, Class<T> type) {
        checkOpen();
        return actual.getObjects(refs, listener, type);
    }
}
