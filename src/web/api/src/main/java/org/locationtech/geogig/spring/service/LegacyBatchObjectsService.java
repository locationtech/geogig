/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 * 
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.remote.http.BinaryPackedObjects;
import org.locationtech.geogig.remotes.internal.DeduplicationService;
import org.locationtech.geogig.remotes.internal.Deduplicator;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.BatchObjects;
import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 *
 */
@Service("legacyBatchObjectsService")
public class LegacyBatchObjectsService extends AbstractRepositoryService {

    public BatchObjects batchObjects(RepositoryProvider provider, String repoName,
            InputStream request) {
        final BatchObjects batchObjects = new BatchObjects();
        final Reader body = new InputStreamReader(request);
        final JsonParser parser = new JsonParser();
        final JsonElement messageJson = parser.parse(body);
        final ArrayList<ObjectId> want = new ArrayList<>();
        final ArrayList<ObjectId> have = new ArrayList<>();

        if (messageJson.isJsonObject()) {
            final JsonObject message = messageJson.getAsJsonObject();
            final JsonArray wantArray;
            final JsonArray haveArray;
            if (message.has("want") && message.get("want").isJsonArray()) {
                wantArray = message.get("want").getAsJsonArray();
            } else {
                wantArray = new JsonArray();
            }
            if (message.has("have") && message.get("have").isJsonArray()) {
                haveArray = message.get("have").getAsJsonArray();
            } else {
                haveArray = new JsonArray();
            }
            for (final JsonElement e : wantArray) {
                if (e.isJsonPrimitive()) {
                    want.add(ObjectId.valueOf(e.getAsJsonPrimitive().getAsString()));
                }
            }
            for (final JsonElement e : haveArray) {
                if (e.isJsonPrimitive()) {
                    have.add(ObjectId.valueOf(e.getAsJsonPrimitive().getAsString()));
                }
            }
        }

        final Repository repository = getRepository(provider, repoName);
        final Deduplicator deduplicator = DeduplicationService.create();
        BinaryPackedObjects packer = new BinaryPackedObjects(repository.objectDatabase());
        
        batchObjects.setDeduplicator(deduplicator).setHave(have).setPacker(packer).setWant(want);
        return batchObjects;
    }
}
