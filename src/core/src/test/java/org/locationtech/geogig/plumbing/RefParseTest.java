/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.storage.RefDatabase;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

/**
 *
 */
public class RefParseTest {

    private RefDatabase mockRefDb;

    private RefParse command;

    @Before
    public void setUp() {

        mockRefDb = mock(RefDatabase.class);

        Builder<String, String> builder = ImmutableMap.builder();
        Map<String, String> allRefs = builder//
                .put("refs/heads/master", RevObjectTestSupport.hashString("refs/heads/master").toString())//
                .put("refs/heads/branch1", RevObjectTestSupport.hashString("refs/heads/branch1").toString())//
                .put("refs/heads/v1.1", RevObjectTestSupport.hashString("refs/heads/v1.1").toString())//
                .put("refs/tags/tag1", RevObjectTestSupport.hashString("refs/tags/tag1").toString())//
                .put("refs/tags/v1.1", RevObjectTestSupport.hashString("refs/tags/v1.1").toString())//
                .put("refs/remotes/origin/master",
                        RevObjectTestSupport.hashString("refs/remotes/origin/master").toString())//
                .put("refs/remotes/origin/branch1",
                        RevObjectTestSupport.hashString("refs/remotes/origin/branch1").toString())//
                .put("refs/remotes/juan/master", RevObjectTestSupport.hashString("refs/remotes/juan/master").toString())//
                .put("refs/remotes/juan/v1.1", RevObjectTestSupport.hashString("refs/remotes/juan/v1.1").toString())//
                .build();

        when(mockRefDb.getAll()).thenReturn(allRefs);
        command = new RefParse();
        for (String name : allRefs.keySet()) {
            when(mockRefDb.getRef(eq(name))).thenReturn(allRefs.get(name));
        }

        Context mockCommandLocator = mock(Context.class);
        when(mockCommandLocator.refDatabase()).thenReturn(mockRefDb);
        command.setContext(mockCommandLocator);
        ResolveObjectType mockResolveObjectType = mock(ResolveObjectType.class);
        when(mockCommandLocator.command(eq(ResolveObjectType.class)))
                .thenReturn(mockResolveObjectType);

        when(mockResolveObjectType.setObjectId((ObjectId) anyObject()))
                .thenReturn(mockResolveObjectType);
        when(mockResolveObjectType.call()).thenReturn(TYPE.COMMIT);
    }

    @Test
    public void testPreconditions() {
        try {
            command.call();
            fail("expected ISE");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("name has not been set"));
        }
    }

    @Test
    public void testNonExistentRef() {
        assertFalse(command.setName("HEADs").call().isPresent());
        assertFalse(command.setName("remotes/upstream").call().isPresent());
        assertFalse(command.setName("refs/remotes/origin/badbranch").call().isPresent());
    }

    @Test
    public void testParseCompleteRef() {
        String refName = "refs/heads/master";

        Optional<Ref> ref = command.setName(refName).call();
        assertTrue(ref.isPresent());
        assertEquals(refName, ref.get().getName());
        assertEquals(RevObjectTestSupport.hashString(refName), ref.get().getObjectId());

        refName = "refs/remotes/juan/v1.1";

        testRsolvePartial(refName, refName);
    }

    @Test
    public void testResolvePartial() {
        testRsolvePartial("master", "refs/heads/master");
        testRsolvePartial("heads/master", "refs/heads/master");
        testRsolvePartial("branch1", "refs/heads/branch1");
        testRsolvePartial("v1.1", "refs/heads/v1.1");
        testRsolvePartial("remotes/juan/master", "refs/remotes/juan/master");
        testRsolvePartial("juan/master", "refs/remotes/juan/master");
        testRsolvePartial("tags/v1.1", "refs/tags/v1.1");
        testRsolvePartial("tag1", "refs/tags/tag1");
    }

    private void testRsolvePartial(String refSpec, String refName) {
        Optional<Ref> ref;
        ref = command.setName(refSpec).call();
        assertTrue(ref.isPresent());
        assertEquals(refName, ref.get().getName());
        assertEquals(RevObjectTestSupport.hashString(refName), ref.get().getObjectId());
    }

    @Test
    public void testResolveSymbolicRef() {
        when(mockRefDb.getRef(eq("HEAD"))).thenThrow(new IllegalArgumentException());
        when(mockRefDb.getSymRef(eq("HEAD"))).thenReturn("refs/heads/branch1");
        Optional<Ref> ref = command.setName("HEAD").call();
        assertTrue(ref.isPresent());
        assertTrue(ref.get() instanceof SymRef);
        assertEquals("HEAD", ref.get().getName());
        assertEquals("refs/heads/branch1", ((SymRef) ref.get()).getTarget());
    }
}
