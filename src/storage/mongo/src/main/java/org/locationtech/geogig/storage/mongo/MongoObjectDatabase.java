/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.mongo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTag;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.BulkOpListener;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.ObjectInserter;
import org.locationtech.geogig.storage.ObjectSerializingFactory;
import org.locationtech.geogig.storage.ObjectWriter;
import org.locationtech.geogig.storage.datastream.DataStreamSerializationFactoryV1;

import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.BulkWriteOperation;
import com.mongodb.BulkWriteResult;
import com.mongodb.BulkWriteUpsert;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.ning.compress.lzf.LZFInputStream;
import com.ning.compress.lzf.LZFOutputStream;

/**
 * An Object database that uses a MongoDB server for persistence.
 * 
 * @see http://mongodb.com/
 */
public class MongoObjectDatabase implements ObjectDatabase {
    private final MongoConnectionManager manager;

    protected final ConfigDatabase config;

    private MongoClient client = null;

    protected DB db = null;

    protected DBCollection collection = null;

    protected ObjectSerializingFactory serializers = DataStreamSerializationFactoryV1.INSTANCE;

    private String collectionName;

    private ExecutorService executor;

    @Inject
    public MongoObjectDatabase(ConfigDatabase config, MongoConnectionManager manager,
            ExecutorService executor) {
        this(config, manager, "objects", executor);
    }

    MongoObjectDatabase(ConfigDatabase config, MongoConnectionManager manager,
            String collectionName, ExecutorService executor) {
        this.config = config;
        this.manager = manager;
        this.executor = executor;
        this.collectionName = collectionName;
    }

    private RevObject fromBytes(ObjectId id, byte[] buffer) {
        ByteArrayInputStream byteStream = new ByteArrayInputStream(buffer);
        RevObject result;
        try {
            result = serializers.createObjectReader().read(id, new LZFInputStream(byteStream));
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        return result;
    }

    private byte[] toBytes(RevObject object) {
        ObjectWriter<RevObject> writer = serializers.createObjectWriter(object.getType());
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        LZFOutputStream cOut = new LZFOutputStream(byteStream);
        try {
            writer.write(object, cOut);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            cOut.close();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        return byteStream.toByteArray();
    }

    protected String getCollectionName() {
        return collectionName;
    }

    @Override
    public synchronized void open() {
        if (client != null) {
            return;
        }
        String uri = config.get("mongodb.uri").get();
        String database = config.get("mongodb.database").get();
        client = manager.acquire(new MongoAddress(uri));
        db = client.getDB(database);
        collection = db.getCollection(getCollectionName());
        collection.ensureIndex("oid");
    }

    @Override
    public synchronized boolean isOpen() {
        return client != null;
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.OBJECT.configure(config, "mongodb", "0.1");
        String uri = config.get("mongodb.uri").or(config.getGlobal("mongodb.uri"))
                .or("mongodb://localhost:27017/");
        String database = config.get("mongodb.database").or(config.getGlobal("mongodb.database"))
                .or("geogig");
        config.put("mongodb.uri", uri);
        config.put("mongodb.database", database);
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.OBJECT.verify(config, "mongodb", "0.1");
    }

    @Override
    public synchronized void close() {
        if (client != null) {
            manager.release(client);
        }
        client = null;
        db = null;
        collection = null;
    }

    @Override
    public boolean exists(ObjectId id) {
        DBObject query = new BasicDBObject();
        query.put("oid", id.toString());
        return collection.find(query).hasNext();
    }

    @Override
    public List<ObjectId> lookUp(final String partialId) {
        if (partialId.matches("[a-fA-F0-9]+")) {
            DBObject regex = new BasicDBObject();
            regex.put("$regex", "^" + partialId);
            DBObject query = new BasicDBObject();
            query.put("oid", regex);
            DBCursor cursor = collection.find(query);
            List<ObjectId> ids = new ArrayList<ObjectId>();
            while (cursor.hasNext()) {
                DBObject elem = cursor.next();
                String oid = (String) elem.get("oid");
                ids.add(ObjectId.valueOf(oid));
            }
            return ids;
        } else {
            throw new IllegalArgumentException(
                    "Prefix query must be done with hexadecimal values only");
        }
    }

    @Override
    public RevObject get(ObjectId id) {
        RevObject result = getIfPresent(id);
        if (result != null) {
            return result;
        } else {
            throw new NoSuchElementException("No object with id: " + id);
        }
    }

    @Override
    public <T extends RevObject> T get(ObjectId id, Class<T> clazz) {
        return clazz.cast(get(id));
    }

    @Override
    public RevObject getIfPresent(ObjectId id) {
        DBObject query = new BasicDBObject();
        query.put("oid", id.toString());
        DBCursor results = collection.find(query);
        if (results.hasNext()) {
            DBObject result = results.next();
            return fromBytes(id, (byte[]) result.get("serialized_object"));
        } else {
            return null;
        }
    }

    @Override
    public <T extends RevObject> T getIfPresent(ObjectId id, Class<T> clazz) {
        return clazz.cast(getIfPresent(id));
    }

    @Override
    public RevTree getTree(ObjectId id) {
        return get(id, RevTree.class);
    }

    @Override
    public RevFeature getFeature(ObjectId id) {
        return get(id, RevFeature.class);
    }

    @Override
    public RevFeatureType getFeatureType(ObjectId id) {
        return get(id, RevFeatureType.class);
    }

    @Override
    public RevCommit getCommit(ObjectId id) {
        return get(id, RevCommit.class);
    }

    @Override
    public RevTag getTag(ObjectId id) {
        return get(id, RevTag.class);
    }

    private long deleteChunk(List<ObjectId> ids) {
        List<String> idStrings = Lists.transform(ids, Functions.toStringFunction());
        DBObject query = BasicDBObjectBuilder.start().push("oid").add("$in", idStrings).pop().get();
        WriteResult result = collection.remove(query);
        return result.getN();
    }

    @Override
    public boolean delete(ObjectId id) {
        DBObject query = new BasicDBObject();
        query.put("oid", id.toString());
        return collection.remove(query).getLastError().ok();
    }

    @Override
    public long deleteAll(Iterator<ObjectId> ids) {
        return deleteAll(ids, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public long deleteAll(Iterator<ObjectId> ids, BulkOpListener listener) {
        Iterator<List<ObjectId>> chunks = Iterators.partition(ids, 500);
        long count = 0;
        while (chunks.hasNext()) {
            count += deleteChunk(chunks.next());
        }
        return count;
    }

    @Override
    public boolean put(final RevObject object) {
        DBObject query = new BasicDBObject();
        query.put("oid", object.getId().toString());
        DBObject record = toDocument(object);
        return collection.update(query, record, true, false).getLastError().ok();
    }

    private DBObject toDocument(final RevObject object) {
        DBObject record = new BasicDBObject();
        record.put("oid", object.getId().toString());
        record.put("serialized_object", toBytes(object));
        return record;
    }

    @Override
    public void putAll(final Iterator<? extends RevObject> objects) {
        putAll(objects, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public void putAll(Iterator<? extends RevObject> objects, BulkOpListener listener) {
        Preconditions.checkNotNull(executor, "executor service not set");
        if (!objects.hasNext()) {
            return;
        }

        final int bulkSize = 1000;
        final int maxRunningTasks = 10;

        final AtomicBoolean cancelCondition = new AtomicBoolean();

        List<ObjectId> ids = Lists.newArrayListWithCapacity(bulkSize);
        List<Future<?>> runningTasks = new ArrayList<Future<?>>(maxRunningTasks);

        BulkWriteOperation bulkOperation = collection.initializeOrderedBulkOperation();
        try {
            while (objects.hasNext()) {
                RevObject object = objects.next();
                bulkOperation.insert(toDocument(object));

                ids.add(object.getId());

                if (ids.size() == bulkSize || !objects.hasNext()) {
                    InsertTask task = new InsertTask(bulkOperation, listener, ids, cancelCondition);
                    runningTasks.add(executor.submit(task));

                    if (objects.hasNext()) {
                        bulkOperation = collection.initializeOrderedBulkOperation();
                        ids = Lists.newArrayListWithCapacity(bulkSize);
                    }
                }
                if (runningTasks.size() == maxRunningTasks) {
                    waitForTasks(runningTasks);
                }
            }
            waitForTasks(runningTasks);
        } catch (RuntimeException e) {
            cancelCondition.set(true);
            throw e;
        }
    }

    private void waitForTasks(List<Future<?>> runningTasks) {
        // wait...
        for (Future<?> f : runningTasks) {
            try {
                f.get();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        runningTasks.clear();
    }

    private static class InsertTask implements Runnable {

        private BulkWriteOperation bulkOperation;

        private BulkOpListener listener;

        private List<ObjectId> ids;

        private AtomicBoolean cancelCondition;

        public InsertTask(BulkWriteOperation bulkOperation, BulkOpListener listener,
                List<ObjectId> ids, AtomicBoolean cancelCondition) {
            this.bulkOperation = bulkOperation;
            this.listener = listener;
            this.ids = ids;
            this.cancelCondition = cancelCondition;
        }

        @Override
        public void run() {
            if (cancelCondition.get()) {
                return;
            }
            BulkWriteResult bulkResult = bulkOperation.execute(WriteConcern.ACKNOWLEDGED);
            List<BulkWriteUpsert> upserts = bulkResult.getUpserts();

            for (BulkWriteUpsert upsert : upserts) {
                if (cancelCondition.get()) {
                    return;
                }
                int index = upsert.getIndex();
                ObjectId existing = ids.set(index, null);
                listener.found(existing, null);
            }
            for (ObjectId inserted : ids) {
                if (cancelCondition.get()) {
                    return;
                }
                if (inserted != null) {
                    listener.inserted(inserted, null);
                }
            }

            ids.clear();
        }

    }

    @Override
    public ObjectInserter newObjectInserter() {
        return new ObjectInserter(this);
    }

    @Override
    public Iterator<RevObject> getAll(Iterable<ObjectId> ids) {
        return getAll(ids, BulkOpListener.NOOP_LISTENER);
    }

    @Override
    public Iterator<RevObject> getAll(final Iterable<ObjectId> ids, final BulkOpListener listener) {

        return new AbstractIterator<RevObject>() {
            final Iterator<ObjectId> queryIds = ids.iterator();

            @Override
            protected RevObject computeNext() {
                RevObject obj = null;
                while (obj == null) {
                    if (!queryIds.hasNext()) {
                        return endOfData();
                    }
                    ObjectId id = queryIds.next();
                    obj = getIfPresent(id);
                    if (obj == null) {
                        listener.notFound(id);
                    } else {
                        listener.found(obj.getId(), null);
                    }
                }
                return obj == null ? endOfData() : obj;
            }
        };
    }

    public DBCollection getCollection(String name) {
        return db.getCollection(name);
    }

    @Override
    public String toString() {
        return String.format("%s[db: %s, collection: %s]", getClass().getSimpleName(),
                db == null ? "<unset>" : db, collectionName);
    }
}
