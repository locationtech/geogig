/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.di;

import java.util.Iterator;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ForwardingObjectDatabase;
import org.locationtech.geogig.storage.GraphDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.StagingDatabase;

import com.google.common.base.Function;
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
        boolean canDecorate = subject instanceof ObjectDatabase
                && !(subject instanceof StagingDatabase);
        return canDecorate;
    }

    @SuppressWarnings("unchecked")
    @Override
    public ObjectDatabase decorate(Object subject) {
        return new GraphUpdatingObjectDatabase(graphDb, (ObjectDatabase) subject);
    }

    private static class GraphUpdatingObjectDatabase extends ForwardingObjectDatabase {

        private Provider<GraphDatabase> graphDb;

        public GraphUpdatingObjectDatabase(Provider<GraphDatabase> graphDb, ObjectDatabase subject) {
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

            //final List<RevCommit> addedCommits = Lists.newLinkedList();

            final Iterator<? extends RevObject> collectingIterator = Iterators.transform(objects,
                    new Function<RevObject, RevObject>() {

                        private final GraphDatabase graphDatabase = graphDb.get();

                        @Override
                        public RevObject apply(RevObject input) {
                            if (input instanceof RevCommit) {
                                RevCommit commit = (RevCommit) input;
                                ObjectId commitId = commit.getId();
                                ImmutableList<ObjectId> parentIds = commit.getParentIds();
                                graphDatabase.put(commitId, parentIds);

                                // addedCommits.add((RevCommit) input);
                            }
                            return input;
                        }
                    });

            super.putAll(collectingIterator, listener);

//            if (!addedCommits.isEmpty()) {
//                GraphDatabase graphDatabase = graphDb.get();
//                for (RevCommit commit : addedCommits) {
//                    ObjectId commitId = commit.getId();
//                    ImmutableList<ObjectId> parentIds = commit.getParentIds();
//                    graphDatabase.put(commitId, parentIds);
//                }
//            }
        }

    }

}
