/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.controller;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.locationtech.geogig.storage.datastream.FormatCommonV1.readObjectId;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Test;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.storage.RevObjectSerializer;
import org.locationtech.geogig.storage.datastream.DataStreamRevObjectSerializerV1;
import org.locationtech.geogig.storage.datastream.FormatCommonV1;
import org.locationtech.geogig.test.TestData;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.google.common.base.Optional;
import com.google.common.collect.AbstractIterator;
import com.google.gson.JsonObject;

/**
 *
 */
public class FilteredChangesControllerTest extends AbstractControllerTest {

    @Test
    public void testNoCommit() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();

        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/filteredchanges");
        perform(post).andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString(
                        "Object does not exist: 0000000000000000000000000000000000000000")));
        repo.close();
    }

    @Test
    public void testFilteredChanges() throws Exception {
        Repository repo = repoProvider.createGeogig("repo1", null);
        repoProvider.getTestRepository("repo1").initializeRpository();
        // setup TestData with branches: master, branch1 and branch2
        new TestData(repo).init("testGeoGig", "geogig@geogig.org").loadDefaultData();
        Optional<RevObject> masterRevObject = repo.command(RevObjectParse.class)
                .setRefSpec("master").call();
        String commitId = masterRevObject.get().getId().toString();
        JsonObject json = new JsonObject();
        json.addProperty("commitId", commitId);
        MockHttpServletRequestBuilder post =
                MockMvcRequestBuilders.post("/repos/repo1/repo/filteredchanges").contentType(
                        MediaType.APPLICATION_JSON).content(getBytes(json));
        byte[] content = perform(post).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn().getResponse().getContentAsByteArray();
        // build an iterator for the content bytes
        FilteredChangesReader reader = new FilteredChangesReader(content,
                DataStreamRevObjectSerializerV1.INSTANCE);
        while (reader.hasNext()) {
            DiffPacket diff = reader.next();
            // ensure the DiffEntry, RevObject and MetaData are not null
            assertNotNull(diff.revObject);
            assertNotNull(diff.metadataObject);
            assertNotNull(diff.diffEntry);
        }
        // shouldn't be a filtered diff
        assertFalse(reader.isFiltered());
        repo.close();
    }

    private static enum CHUNK_TYPE {
        DIFF_ENTRY {
            @Override
            public int value() {
                return 0;
            }
        },
        OBJECT_AND_DIFF_ENTRY {
            @Override
            public int value() {
                return 1;
            }
        },
        METADATA_OBJECT_AND_DIFF_ENTRY {
            @Override
            public int value() {
                return 2;
            }
        },
        FILTER_FLAG {
            @Override
            public int value() {
                return 3;
            }
        };

        public abstract int value();

        private static final CHUNK_TYPE[] values = CHUNK_TYPE.values();

        public static CHUNK_TYPE valueOf(int value) {
            // abusing the fact that value() coincides with ordinal()
            return values[value];
        }
    };

    private static class FilteredChangesReader extends AbstractIterator<DiffPacket> {

        private final ByteArrayInputStream in;
        private final DataInput data;
        private boolean filtered;
        private final RevObjectSerializer serializer;

        private FilteredChangesReader(byte[] bytes, RevObjectSerializer serializer) {
            this.in = new ByteArrayInputStream(bytes);
            this.data = new DataInputStream(in);
            this.serializer = serializer;
        }

        boolean isFiltered() {
            return this.filtered;
        }

        @Override
        protected DiffPacket computeNext() {
            try {
                final CHUNK_TYPE chunkType = CHUNK_TYPE.valueOf((int) (data.readByte() & 0xFF));

                RevObject revObj = null;
                RevObject metadata = null;

                switch (chunkType) {
                    case DIFF_ENTRY:
                        break;
                    case OBJECT_AND_DIFF_ENTRY: {
                        ObjectId id = readObjectId(data);
                        revObj = serializer.read(id, in);
                    }
                    break;
                    case METADATA_OBJECT_AND_DIFF_ENTRY: {
                        ObjectId mdid = readObjectId(data);
                        metadata = serializer.read(mdid, in);
                        ObjectId id = readObjectId(data);
                        revObj = serializer.read(id, in);
                    }
                    break;
                    case FILTER_FLAG: {
                        int changesFiltered = in.read();
                        if (changesFiltered != 0) {
                            filtered = true;
                        }
                        return endOfData();
                    }
                    default:
                        throw new IllegalStateException("Unknown chunk type: " + chunkType);
                }

                DiffEntry diff = FormatCommonV1.readDiff(data);
                return new DiffPacket(diff, revObj, metadata);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

    }

    private static class DiffPacket {

        final DiffEntry diffEntry;

        @Nullable
        final RevObject revObject;

        @Nullable
        final RevObject metadataObject;

        public DiffPacket(DiffEntry entry, @Nullable RevObject newObject,
                @Nullable RevObject metadata) {
            this.diffEntry = entry;
            this.revObject = newObject;
            this.metadataObject = metadata;
        }
    }

}
