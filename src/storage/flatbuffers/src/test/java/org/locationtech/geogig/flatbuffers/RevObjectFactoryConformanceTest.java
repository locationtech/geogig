/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.flatbuffers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevObjects;
import org.locationtech.geogig.model.RevPerson;
import org.locationtech.geogig.model.impl.RevObjectFactoryImpl;

import com.google.common.base.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

public abstract class RevObjectFactoryConformanceTest {

    private RevObjectFactory factory;

    private @RequiredArgsConstructor @EqualsAndHashCode static class RevPersonImpl
            implements RevPerson {

        private final String name, email;

        private final @Getter long timestamp;

        private final @Getter int timeZoneOffset;

        public @Override Optional<String> getName() {
            return Optional.fromNullable(name);
        }

        public @Override Optional<String> getEmail() {
            return Optional.fromNullable(email);
        }

        public @Override boolean equals(Object o) {
            return (o instanceof RevPerson) && RevObjects.equals(this, ((RevPerson) o));
        }

        public @Override int hashCode() {
            return RevObjects.hashCode(this);
        }

        public @Override String toString() {
            return RevObjects.toString(this);
        }

    }

    public @Before void setUp() throws Exception {
        factory = newFactory();
    }

    public @After void tearDown() throws Exception {
    }

    protected abstract RevObjectFactory newFactory();

    public @Test final void testCreateCommit() throws IOException {
        ObjectId id = ObjectId.create(1, 2, 3);
        ObjectId treeId = ObjectId.create(4, 5, 6);
        List<ObjectId> parents = Collections.emptyList();
        RevPerson author = new RevPersonImpl("Gabe", "gabe@example.com", 10000, -3);
        RevPerson committer = new RevPersonImpl("Dave", null, 50000, -6);
        String message = "sample commit message";

        RevCommit c1 = new RevObjectFactoryImpl().createCommit(id, treeId, parents, author,
                committer, message);
        FlatBuffersRevObjectSerializer encoder = new FlatBuffersRevObjectSerializer();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        encoder.write(c1, out);
        RevCommit c11 = (RevCommit) encoder.read(c1.getId(),
                new ByteArrayInputStream(out.toByteArray()));
        assertEquals(c1.getId(), c11.getId());
        assertEquals(c1.getCommitter(), c11.getCommitter());
        assertEquals(c1.getAuthor(), c11.getAuthor());
        assertEquals(c1.getMessage(), c11.getMessage());
        assertEquals(c1.getParentIds(), c11.getParentIds());
        assertEquals(c1.getTreeId(), c11.getTreeId());
        assertEquals(c1.getType(), c11.getType());

        RevCommit commit = factory.createCommit(id, treeId, parents, author, committer, message);
        assertNotNull(commit);
    }

    public @Test final void testCreateTreeObjectIdLongListOfNodeListOfNode() {
        fail("Not yet implemented");
    }

    public @Test final void testCreateTreeObjectIdLongIntSortedMapOfIntegerBucket() {
        fail("Not yet implemented");
    }

    public @Test final void testCreateTag() {
        fail("Not yet implemented");
    }

    public @Test final void testCreateFeatureType() {
        fail("Not yet implemented");
    }

    public @Test final void testCreateFeatureObjectIdListOfObject() {
        fail("Not yet implemented");
    }

    public @Test final void testCreateFeatureObjectIdObjectArray() {
        fail("Not yet implemented");
    }

}
