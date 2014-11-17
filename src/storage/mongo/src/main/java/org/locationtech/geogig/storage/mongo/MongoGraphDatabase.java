/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.storage.mongo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabase;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

/**
 * A graph database that uses a MongoDB server for persistence.
 */
public class MongoGraphDatabase implements GraphDatabase {
    private final MongoConnectionManager manager;

    private final ConfigDatabase config;

    private MongoClient client;
    private DBCollection collection;

    @Inject
    public MongoGraphDatabase(final MongoConnectionManager manager, final ConfigDatabase config) {
        this.config = config;
        this.manager = manager;
    }

    @Override
    public void configure() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.GRAPH.configure(config, "mongodb", "0.1");
    }

    @Override
    public void checkConfig() throws RepositoryConnectionException {
        RepositoryConnectionException.StorageType.GRAPH.verify(config, "mongodb", "0.1");
    }

    private class MongoNode extends GraphNode {
        private DBObject dbObject;

        public MongoNode(DBObject dbObject) {
            this.dbObject = dbObject;
        }

        @Override
        public boolean isSparse() {
            DBObject properties = (DBObject) dbObject.get("_properties");
            return Boolean.valueOf((String) properties.get(SPARSE_FLAG));
        }

        @Override
        public ObjectId getIdentifier() {
            DBObject properties = (DBObject) dbObject.get("_properties");
            return ObjectId.valueOf((String) properties.get("identifier"));
        }

        @Override
        public Iterator<GraphEdge> getEdges(final Direction direction) {
            DBObject properties = (DBObject) dbObject.get("_properties");
            final String id = (String) properties.get("identifier");

            DBObject query = new BasicDBObject();
            switch (direction) {
                case OUT:
                    query.put("_in", id);
                    break;
                case IN:
                    query.put("_out", id);
                    break;
                case BOTH:
                    DBObject in = new BasicDBObject("_in", id);
                    DBObject out = new BasicDBObject("_out", id);
                    query.put("$or", new DBObject[]{ in, out });
                    break;
                default: throw new IllegalStateException("Unexpected direction value");
            }

            DBCursor cursor = collection.find(query);
            Function<DBObject, GraphEdge> mapper = new Function<DBObject, GraphEdge>() {
                @Override
                public GraphEdge apply(DBObject dbObject) {
                    GraphNode in, out;

                    if (id.equals(dbObject.get("_out"))) {
                        in = getNode(ObjectId.valueOf((String)dbObject.get("_in")));
                        out = MongoNode.this;
                    } else {
                        out = getNode(ObjectId.valueOf((String)dbObject.get("_out")));
                        in = MongoNode.this;
                    }
                    return new GraphEdge(in, out);
                }
            };
            return Iterators.transform(cursor.iterator(), mapper);
        }

        @Override
        public String toString() {
            return getIdentifier().toString();
        }
    }

    @Override
    public void open() {
        String uri = config.get("mongodb.uri").get();
        String database = config.get("mongodb.database").get();
        this.client = manager.acquire(new MongoAddress(uri));
        DB db = client.getDB(database);
        this.collection = db.getCollection("graph");
    }

    @Override
    public boolean isOpen() {
        return this.client != null;
    }

    @Override
    public void close() {
        this.client.close();
        this.client = null;
        this.collection = null;
    }

    @Override
    public boolean exists(ObjectId id) {
        DBObject query = idQuery(id);
        DBObject result = collection.findOne(query);
        return result != null;
    }

    private DBObject idQuery(ObjectId id) {
        DBObject query = new BasicDBObject("_properties.identifier", id.toString());
        return query;
    }

    @Override
    public void map(ObjectId id, ObjectId mappedId) {
        DBObject query = new BasicDBObject();
        query.put("_label", Relationship.MAPPED_TO.name());
        query.put("_out", id.toString());

        DBObject edge = collection.findOne(query);
        if (edge != null) {
            edge.put("_in", mappedId.toString());
            collection.save(edge);
        } else {
            edge = new BasicDBObject();
            edge.put("_label", Relationship.MAPPED_TO.name());
            edge.put("_out", id.toString());
            edge.put("_in", mappedId.toString());
            collection.insert(edge);
        }
    }

    @Override
    public boolean put(ObjectId id, ImmutableList<ObjectId> ids) {
        DBObject query = idQuery(id);
        DBObject result = collection.findOne(query);
        if (result != null) {
            return false;
        } else {
            DBObject record = new BasicDBObject();
            record.put("_properties", new BasicDBObject("identifier", id.toString()));
            collection.insert(record);
            for (ObjectId parent : ids) {
                DBObject edge = new BasicDBObject();
                edge.put("_label", Relationship.PARENT.name());
                edge.put("_in", id.toString());
                edge.put("_out", parent.toString());
                collection.insert(edge);
            }
            return true;
        }
    }

    @Override
    public ImmutableList<ObjectId> getChildren(ObjectId id) {
        DBObject query = new BasicDBObject();
        query.put("_label", Relationship.PARENT.name());
        query.put("_out", id.toString());
        DBCursor cursor = collection.find(query);

        Function<DBObject, ObjectId> idMapper = new Function<DBObject, ObjectId>() {
            @Override
            public ObjectId apply(DBObject o) {
                return ObjectId.valueOf((String)o.get("_in"));
            }
        };

        return ImmutableList.copyOf(Iterators.transform(cursor.iterator(), idMapper));
    }

    @Override
    public int getDepth(ObjectId id) {
        int depth = 0;
        Set<ObjectId> front = new HashSet<ObjectId>();
        front.add(id);

        Function<ObjectId, String> idMapper = new Function<ObjectId, String>() {
            @Override
            public String apply(ObjectId id) {
                return id.toString();
            }
        };

        while (front.size() > 0) {
            DBObject query = new BasicDBObject();
            query.put("_label", Relationship.PARENT.name());
            query.put("_in", new BasicDBObject("$in", Lists.transform(new ArrayList<ObjectId>(front), idMapper)));
            DBCursor result = collection.find(query);
            Set<ObjectId> nextFront = new HashSet<ObjectId>();
            for (DBObject o : result) {
                front.remove(ObjectId.valueOf((String) o.get("_in")));
                nextFront.add(ObjectId.valueOf((String) o.get("_out")));
            }
            if (front.size() > 0) {
                break;
            } else {
                front = nextFront;
                depth += 1;
            }
        }
        return depth;
    }

    @Override
    public ObjectId getMapping(ObjectId mappedId) {
        DBObject query = new BasicDBObject();
        query.put("_out", mappedId.toString());
        query.put("_label", Relationship.MAPPED_TO.name());
        DBObject result = collection.findOne(query);
        if (result == null) return null;
        return ObjectId.valueOf((String) result.get("_in"));
    }

    @Override
    public GraphNode getNode(ObjectId id) {
        DBObject query = idQuery(id);
        DBObject result = collection.findOne(query);
        if (result == null) throw new RuntimeException("No such node: " + id);
        return new MongoNode(result);
    }

    @Override
    public ImmutableList<ObjectId> getParents(ObjectId id) {
        DBObject query = new BasicDBObject();
        query.put("_label", Relationship.PARENT.name());
        query.put("_in", id.toString());
        DBCursor cursor = collection.find(query);

        Function<DBObject, ObjectId> idMapper = new Function<DBObject, ObjectId>() {
            @Override
            public ObjectId apply(DBObject o) {
                return ObjectId.valueOf((String)o.get("_out"));
            }
        };
        return ImmutableList.copyOf(Iterators.transform(cursor.iterator(), idMapper));
    }

    @Override
    public void setProperty(ObjectId id, String name, String value) {
        DBObject query = idQuery(id);
        DBObject record = collection.findOne(query);
        DBObject properties = (DBObject) record.get("_properties");
        properties.put(name, value);
        record.put("_properties", properties);
        collection.save(record);
    }

    @Override
    public void truncate() {
        // NO-OP
    }

    @Override
    public String toString() {
        return String.format("%s[uri: %s]", getClass().getSimpleName(),
                config == null ? "<unknown>" : config.get("mongodb.uri").or("<unset>"));
    }
}
