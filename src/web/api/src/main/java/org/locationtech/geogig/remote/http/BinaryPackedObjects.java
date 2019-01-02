/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.remote.http;

import static java.lang.String.format;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.remotes.internal.Deduplicator;
import org.locationtech.geogig.remotes.internal.ObjectFunnel;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.BulkOpListener.CountingListener;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;

public final class BinaryPackedObjects {

    private static final Logger LOGGER = LoggerFactory.getLogger(BinaryPackedObjects.class);

    private final RevObjectSerializer marshaller;

    private final ObjectStore database;

    public BinaryPackedObjects(ObjectStore database) {
        this.database = database;
        this.marshaller = DataStreamRevObjectSerializerV1.INSTANCE;
    }

    /**
     * @return the number of objects written
     */
    public long write(ObjectFunnel funnel, List<ObjectId> want, List<ObjectId> have,
            boolean traverseCommits, Deduplicator deduplicator) throws IOException {
        return write(funnel, want, have, new HashSet<ObjectId>(), DEFAULT_CALLBACK, traverseCommits,
                deduplicator);
    }

    /**
     * @return the number of objects written
     */
    public long write(ObjectFunnel funnel, List<ObjectId> want, List<ObjectId> have,
            Set<ObjectId> sent, Callback callback, boolean traverseCommits,
            Deduplicator deduplicator) throws IOException {

        for (ObjectId i : want) {
            if (!database.exists(i)) {
                throw new NoSuchElementException(format("Wanted commit: '%s' is not known", i));
            }
        }

        LOGGER.info("scanning for previsit list...");
        Stopwatch sw = Stopwatch.createStarted();
        ImmutableList<ObjectId> needsPrevisit = traverseCommits
                ? scanForPrevisitList(want, have, deduplicator) : ImmutableList.copyOf(have);
        LOGGER.info(String.format(
                "Previsit list built in %s for %,d ids: %s. Calculating reachable content ids...",
                sw.stop(), needsPrevisit.size(), needsPrevisit));

        deduplicator.reset();

        sw.reset().start();
        ImmutableList<ObjectId> previsitResults = reachableContentIds(needsPrevisit, deduplicator);
        LOGGER.info(String.format("reachableContentIds took %s for %,d ids", sw.stop(),
                previsitResults.size()));

        deduplicator.reset();

        LOGGER.info("obtaining post order iterator on range...");
        sw.reset().start();

        Iterator<RevObject> objects = PostOrderIterator.range(want,
                new ArrayList<ObjectId>(previsitResults), database, traverseCommits, deduplicator);
        long objectCount = 0;
        LOGGER.info("PostOrderIterator.range took {}", sw.stop());

        try {
            LOGGER.info("writing objects to remote...");
            while (objects.hasNext()) {
                RevObject object = objects.next();
                funnel.funnel(object);
                objectCount++;
                callback.callback(Suppliers.ofInstance(object));
            }
        } catch (IOException e) {
            String causeMessage = Throwables.getRootCause(e).getMessage();
            LOGGER.info(String.format("writing of objects failed after %,d objects. Cause: '%s'",
                    objectCount, causeMessage));
            throw e;
        }
        return objectCount;
    }

    /**
     * Find commits which should be previsited to avoid resending objects that are already on the
     * receiving end. A commit should be previsited if:
     * <ul>
     * <li>It is not going to be visited, and
     * <li>It is the immediate ancestor of a commit which is going to be previsited.
     * </ul>
     * 
     */
    private ImmutableList<ObjectId> scanForPrevisitList(List<ObjectId> want, List<ObjectId> have,
            Deduplicator deduplicator) {
        /*
         * @note Implementation note: To find the previsit list, we just iterate over all the
         * commits that will be visited according to our want and have lists. Any parents of commits
         * in this traversal which are part of the 'have' list will be in the previsit list.
         */
        Iterator<RevCommit> willBeVisited = Iterators.filter( //
                PostOrderIterator.rangeOfCommits(want, have, database, deduplicator), //
                RevCommit.class);
        ImmutableSet.Builder<ObjectId> builder = ImmutableSet.builder();

        while (willBeVisited.hasNext()) {
            RevCommit next = willBeVisited.next();
            List<ObjectId> parents = new ArrayList<ObjectId>(next.getParentIds());
            parents.retainAll(have);
            builder.addAll(parents);
        }

        return ImmutableList.copyOf(builder.build());
    }

    private ImmutableList<ObjectId> reachableContentIds(ImmutableList<ObjectId> needsPrevisit,
            Deduplicator deduplicator) {

        Function<RevObject, ObjectId> getIdTransformer = (obj) -> obj == null ? null : obj.getId();

        Iterator<ObjectId> reachable = Iterators.transform( //
                PostOrderIterator.contentsOf(needsPrevisit, database, deduplicator), //
                getIdTransformer);
        return ImmutableList.copyOf(reachable);
    }

    public static class IngestResults {
        private long inserted;

        private long existing;

        private IngestResults(long inserted, long existing) {
            this.inserted = inserted;
            this.existing = existing;

        }

        /**
         * @return the number of objects inserted (i.e. didn't already exist)
         */
        public long getInserted() {
            return inserted;
        }

        /**
         * @return the number of objects that already existed in the objects database
         */
        public long getExisting() {
            return existing;
        }

        public long total() {
            return inserted + existing;
        }
    }

    /**
     * @return the number of objects parsed from the input stream
     */
    public IngestResults ingest(final InputStream in) {
        return ingest(in, DEFAULT_CALLBACK);
    }

    /**
     * @return the number of objects parsed from the input stream
     */
    public IngestResults ingest(final InputStream in, final Callback callback) {
        Iterator<RevObject> objects = streamToObjects(in);

        BulkOpListener listener = new BulkOpListener() {
            @Override
            public void inserted(final ObjectId objectId, @Nullable Integer storageSizeBytes) {
                callback.callback(new Supplier<RevObject>() {
                    @Override
                    public RevObject get() {
                        return database.get(objectId);
                    }
                });
            }
        };

        CountingListener countingListener = BulkOpListener.newCountingListener();
        listener = BulkOpListener.composite(countingListener, listener);
        database.putAll(objects, listener);
        return new IngestResults(countingListener.inserted(), countingListener.found());
    }

    private Iterator<RevObject> streamToObjects(final InputStream in) {
        return new AbstractIterator<RevObject>() {
            @Override
            protected RevObject computeNext() {
                try {
                    ObjectId id = readObjectId(in);
                    RevObject revObj = marshaller.read(id, in);
                    return revObj;
                } catch (EOFException eof) {
                    return endOfData();
                } catch (IOException e) {
                    Throwables.throwIfUnchecked(e);
                    throw new RuntimeException(e);
                }
            }
        };
    }

    private ObjectId readObjectId(final InputStream in) throws IOException {
        final int len = ObjectId.NUM_BYTES;
        byte[] rawBytes = new byte[len];
        int amount = 0;
        int offset = 0;
        while ((amount = in.read(rawBytes, offset, len - offset)) != 0) {
            if (amount < 0)
                throw new EOFException("Came to end of input");
            offset += amount;
            if (offset == len)
                break;
        }
        ObjectId id = ObjectId.create(rawBytes);
        return id;
    }

    public static interface Callback {
        public abstract void callback(Supplier<RevObject> object);
    }

    private static final Callback DEFAULT_CALLBACK = new Callback() {
        @Override
        public void callback(Supplier<RevObject> object) {
            // empty body
        }
    };

}
