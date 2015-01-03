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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.locationtech.geogig.storage.ConflictsDatabase;

import com.google.common.base.Optional;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

/**
 * A staging database that uses a MongoDB server for persistence.
 */
class MongoConflictsDatabase implements ConflictsDatabase {

    private DBCollection conflicts;

    public MongoConflictsDatabase(DB db) {
        conflicts = db.getCollection("conflicts");
        conflicts.ensureIndex("path");
    }

    @Override
    public Optional<Conflict> getConflict(@Nullable String namespace, String path) {
        DBObject query = new BasicDBObject();
        query.put("path", path);
        if (namespace != null) {
            query.put("namespace", namespace);
        }
        DBObject result = conflicts.findOne(query);
        if (result == null) {
            return Optional.absent();
        } else {
            ObjectId ancestor = ObjectId.valueOf((String) result.get("ancestor"));
            ObjectId ours = ObjectId.valueOf((String) result.get("ours"));
            ObjectId theirs = ObjectId.valueOf((String) result.get("theirs"));
            return Optional.of(new Conflict(path, ancestor, ours, theirs));
        }
    }

    @Override
    public boolean hasConflicts(String namespace) {
        DBObject query = new BasicDBObject();
        if (namespace == null) {
            query.put("namespace", 0);
        } else {
            query.put("namespace", namespace);
        }
        long count = conflicts.count(query);
        return count > 0;
    }

    @Override
    public List<Conflict> getConflicts(@Nullable String namespace, @Nullable String pathFilter) {
        DBObject query = new BasicDBObject();
        if (namespace == null) {
            query.put("namespace", 0);
        } else {
            query.put("namespace", namespace);
        }
        if (pathFilter != null) {
            DBObject regex = new BasicDBObject();
            regex.put("$regex", "^" + pathFilter);
            query.put("path", regex);
        }
        DBCursor cursor = conflicts.find(query);
        List<Conflict> results = new ArrayList<Conflict>();
        while (cursor.hasNext()) {
            DBObject element = cursor.next();
            String path = (String) element.get("path");
            ObjectId ancestor = ObjectId.valueOf((String) element.get("ancestor"));
            ObjectId ours = ObjectId.valueOf((String) element.get("ours"));
            ObjectId theirs = ObjectId.valueOf((String) element.get("theirs"));
            results.add(new Conflict(path, ancestor, ours, theirs));
        }
        return results;
    }

    @Override
    public void addConflict(@Nullable String namespace, Conflict conflict) {
        DBObject query = new BasicDBObject();
        query.put("path", conflict.getPath());
        if (namespace == null) {
            query.put("namespace", 0);
        } else {
            query.put("namespace", namespace);
        }
        DBObject record = new BasicDBObject();
        if (namespace == null) {
            record.put("namespace", 0);
        } else {
            record.put("namespace", namespace);
        }
        record.put("path", conflict.getPath());
        record.put("ancestor", conflict.getAncestor().toString());
        record.put("ours", conflict.getOurs().toString());
        record.put("theirs", conflict.getTheirs().toString());
        conflicts.update(query, record, true, false);
    }

    @Override
    public void removeConflict(@Nullable String namespace, String path) {
        DBObject query = new BasicDBObject();
        if (namespace == null) {
            query.put("namespace", 0);
        } else {
            query.put("namespace", namespace);
        }
        query.put("path", path);
        conflicts.remove(query);
    }

    @Override
    public void removeConflicts(@Nullable String namespace) {
        DBObject query = new BasicDBObject();
        if (namespace == null) {
            query.put("namespace", 0);
        } else {
            query.put("namespace", namespace);
        }
        conflicts.remove(query);
    }
}
