/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.di;

import java.util.Iterator;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.impl.ForwardingObjectDatabase;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

/**
 * Method interceptor for {@link ObjectDatabase#put(RevObject)} that adds new commits to the graph
 * database.
 */
class ObjectDatabasePutInterceptor implements Decorator {

    private Provider<GraphDatabase> graphDb;

    public ObjectDatabasePutInterceptor(Provider<GraphDatabase> graphDb) {
        this.graphDb = graphDb;
    }

    @Override
    public boolean canDecorate(Object subject) {
        boolean canDecorate = subject instanceof ObjectDatabase;
        return canDecorate;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ObjectStore decorate(Object subject) {
        return new GraphUpdatingObjectDatabase(graphDb, (ObjectDatabase) subject);
    }

    private static class GraphUpdatingObjectDatabase extends ForwardingObjectDatabase {

        private Provider<GraphDatabase> graphDb;

        public GraphUpdatingObjectDatabase(Provider<GraphDatabase> graphDb,
                ObjectDatabase subject) {
            super(Providers.of(subject));
            this.graphDb = graphDb;
        }

        @Override
        public boolean put(RevObject object) {

            final boolean inserted = super.put(object);

            if (inserted && RevObject.TYPE.COMMIT.equals(object.getType())) {
                RevCommit commit = (RevCommit) object;
                graphDb.get().put(commit.getId(), commit.getParentIds());
            }
            return inserted;
        }

        @Override
        public void putAll(Iterator<? extends RevObject> objects) {
            putAll(objects, BulkOpListener.NOOP_LISTENER);
        }

        @Override
        public void putAll(Iterator<? extends RevObject> objects, BulkOpListener listener) {

            // final List<RevCommit> addedCommits = Lists.newLinkedList();

            final Iterator<? extends RevObject> collectingIterator = Iterators.transform(objects,
                    (obj) -> {
                        if (obj instanceof RevCommit) {
                            final GraphDatabase graphDatabase = graphDb.get();
                            RevCommit commit = (RevCommit) obj;
                            ObjectId commitId = commit.getId();
                            ImmutableList<ObjectId> parentIds = commit.getParentIds();
                            graphDatabase.put(commitId, parentIds);

                            // addedCommits.add((RevCommit) input);
                        }
                        return obj;

                    });
            super.putAll(collectingIterator, listener);

            // if (!addedCommits.isEmpty()) {
            // GraphDatabase graphDatabase = graphDb.get();
            // for (RevCommit commit : addedCommits) {
            // ObjectId commitId = commit.getId();
            // ImmutableList<ObjectId> parentIds = commit.getParentIds();
            // graphDatabase.put(commitId, parentIds);
            // }
            // }
        }

    }

}
