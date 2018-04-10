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
import java.util.HashSet;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.remote.http.BinaryPackedChanges;
import org.locationtech.geogig.remotes.internal.FilteredDiffIterator;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.RepositoryFilter;
import org.locationtech.geogig.rest.repository.RepositoryProvider;
import org.locationtech.geogig.spring.dto.FilteredChanges;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 *
 */
@Service("legacyFilteredChangesService")
public class LegacyFilteredChangesService extends AbstractRepositoryService {

    public FilteredChanges filterChanges(RepositoryProvider provider, String repoName,
            InputStream request) {
        final FilteredChanges filteredChanges = new FilteredChanges();
        try {
            final Reader body = new InputStreamReader(request);
            final JsonParser parser = new JsonParser();
            final JsonElement messageJson = parser.parse(body);

            final HashSet<ObjectId> tracked = new HashSet<>();

            RepositoryFilter filter = new RepositoryFilter();

            ObjectId commitId = ObjectId.NULL;

            if (messageJson.isJsonObject()) {
                final JsonObject message = messageJson.getAsJsonObject();
                final JsonArray trackedArray;
                if (message.has("tracked") && message.get("tracked").isJsonArray()) {
                    trackedArray = message.get("tracked").getAsJsonArray();
                } else {
                    trackedArray = new JsonArray();
                }
                if (message.has("commitId") && message.get("commitId").isJsonPrimitive()) {
                    commitId = ObjectId.valueOf(
                            message.get("commitId").getAsJsonPrimitive().getAsString());
                } else {
                    commitId = ObjectId.NULL;
                }
                for (final JsonElement e : trackedArray) {
                    if (e.isJsonPrimitive()) {
                        tracked.add(ObjectId.valueOf(e.getAsJsonPrimitive().getAsString()));
                    }
                }

                if (message.has("filter") && message.get("filter").isJsonArray()) {
                    JsonArray filterArray = message.get("filter").getAsJsonArray();
                    for (final JsonElement e : filterArray) {
                        if (e.isJsonObject()) {
                            JsonObject filterObject = e.getAsJsonObject();
                            String featureType = null;
                            String filterType = null;
                            String filterText = null;
                            if (filterObject.has("featurepath") &&
                                     filterObject.get("featurepath").isJsonPrimitive()) {
                                featureType = filterObject.get("featurepath")
                                        .getAsJsonPrimitive().getAsString();
                            }
                            if (filterObject.has("type") &&
                                     filterObject.get("type").isJsonPrimitive()) {
                                filterType = filterObject.get("type").getAsJsonPrimitive()
                                        .getAsString();
                            }
                            if (filterObject.has("filter") &&
                                     filterObject.get("filter").isJsonPrimitive()) {
                                filterText = filterObject.get("filter").getAsJsonPrimitive()
                                        .getAsString();
                            }
                            if (featureType != null && filterType != null &&
                                     filterText != null) {
                                filter.addFilter(featureType, filterType, filterText);
                            }
                        }
                    }

                }
            }

            final Repository repository = getRepository(provider, repoName);

            RevCommit commit = repository.getCommit(commitId);

            ObjectId parent = ObjectId.NULL;
            if (commit.getParentIds().size() > 0) {
                parent = commit.getParentIds().get(0);
            }

            AutoCloseableIterator<DiffEntry> changes = repository.command(DiffOp.class)
                    .setNewVersion(commit.getId()).setOldVersion(parent).setReportTrees(true)
                    .call();
            FilteredDiffIterator filteredDiffIterator = new FilteredDiffIterator(changes,
                    repository, filter) {
                @Override
                protected boolean trackingObject(ObjectId objectId) {
                    return tracked.contains(objectId);
                }
            };

            filteredChanges.setChanges(filteredDiffIterator).
                    setPacker(new BinaryPackedChanges(repository));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return filteredChanges;
    }
}
