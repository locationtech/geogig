/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

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

import com.google.common.base.Preconditions;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

public class ForwardingObjectStore implements ObjectStore {

    protected final Provider<? extends ObjectStore> subject;

    protected final boolean canWrite;

    public ForwardingObjectStore(Provider<? extends ObjectStore> odb) {
        this.subject = odb;
        this.canWrite = true;
    }

    public ForwardingObjectStore(final ObjectStore odb) {
        this(odb, false);
    }

    public ForwardingObjectStore(final ObjectStore odb, final boolean readOnly) {
        this.subject = Providers.of(odb);
        this.canWrite = !readOnly;
    }

    protected void checkWritable() {
        Preconditions.checkState(canWrite, "Database is read only");
    }

    @Override
    public void open() {
        subject.get().open();
    }

    @Override
    public boolean isOpen() {
        return subject.get().isOpen();
    }

    @Override
    public void close() {
        subject.get().close();
    }

    @Override
    public boolean exists(ObjectId id) {
        return subject.get().exists(id);
    }

    @Override
    public List<ObjectId> lookUp(String partialId) {
        return subject.get().lookUp(partialId);
    }

    @Override
    public RevObject get(ObjectId id) throws IllegalArgumentException {
        return subject.get().get(id);
    }

    @Override
    public <T extends RevObject> T get(ObjectId id, Class<T> type) throws IllegalArgumentException {
        return subject.get().get(id, type);
    }

    @Override
    public RevObject getIfPresent(ObjectId id) {
        return subject.get().getIfPresent(id);
    }

    @Override
    public <T extends RevObject> T getIfPresent(ObjectId id, Class<T> type)
            throws IllegalArgumentException {
        return subject.get().getIfPresent(id, type);
    }

    @Override
    public RevTree getTree(ObjectId id) {
        return subject.get().getTree(id);
    }

    @Override
    public RevFeature getFeature(ObjectId id) {
        return subject.get().getFeature(id);
    }

    @Override
    public RevFeatureType getFeatureType(ObjectId id) {
        return subject.get().getFeatureType(id);
    }

    @Override
    public RevCommit getCommit(ObjectId id) {
        return subject.get().getCommit(id);
    }

    @Override
    public RevTag getTag(ObjectId id) {
        return subject.get().getTag(id);
    }

    @Override
    public boolean put(RevObject object) {
        checkWritable();
        return subject.get().put(object);
    }

    @Override
    public void delete(ObjectId objectId) {
        checkWritable();
        subject.get().delete(objectId);
    }

    @Override
    public Iterator<RevObject> getAll(Iterable<ObjectId> ids) {
        return subject.get().getAll(ids);
    }

    @Override
    public Iterator<RevObject> getAll(Iterable<ObjectId> ids, BulkOpListener listener) {
        return subject.get().getAll(ids, listener);
    }

    @Override
    public <T extends RevObject> Iterator<T> getAll(Iterable<ObjectId> ids, BulkOpListener listener,
            Class<T> type) {
        return subject.get().getAll(ids, listener, type);
    }

    @Override
    public void putAll(Iterator<? extends RevObject> objects) {
        checkWritable();
        subject.get().putAll(objects);
    }

    @Override
    public void putAll(Iterator<? extends RevObject> objects, BulkOpListener listener) {
        checkWritable();
        subject.get().putAll(objects, listener);
    }

    @Override
    public void deleteAll(Iterator<ObjectId> ids) {
        checkWritable();
        subject.get().deleteAll(ids);
    }

    @Override
    public void deleteAll(Iterator<ObjectId> ids, BulkOpListener listener) {
        checkWritable();
        subject.get().deleteAll(ids, listener);
    }

    @Override
    public String toString() {
        return String.format("%s[%s]", getClass().getSimpleName(), subject);
    }

    @Override
    public <T extends RevObject> AutoCloseableIterator<ObjectInfo<T>> getObjects(
            Iterator<NodeRef> refs, BulkOpListener listener, Class<T> type) {
        return subject.get().getObjects(refs, listener, type);
    }
}
