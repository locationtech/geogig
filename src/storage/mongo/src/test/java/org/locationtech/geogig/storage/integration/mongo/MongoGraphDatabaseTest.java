/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.integration.mongo;

import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.GraphDatabaseTest;
import org.locationtech.geogig.storage.mongo.MongoConnectionManager;
import org.locationtech.geogig.storage.mongo.MongoGraphDatabase;

import com.mongodb.DB;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

public class MongoGraphDatabaseTest extends GraphDatabaseTest {
    @Override
    protected MongoGraphDatabase createDatabase(Platform platform) throws Exception {
        final IniMongoProperties properties = new IniMongoProperties();
        final String uri = properties.get("mongodb.uri", String.class).or("mongodb://localhost:27017/"); 
        final String database = properties.get("mongodb.database", String.class).or("geogig");
        MongoClient client = new MongoClient(new MongoClientURI(uri));
        DB db = client.getDB(database);
        db.dropDatabase();

        MongoConnectionManager manager = new MongoConnectionManager();
        ConfigDatabase config = new TestConfigDatabase(platform);
        MongoGraphDatabase mongoGraphDatabase = new MongoGraphDatabase(manager, config);
        return mongoGraphDatabase;
    }
}
