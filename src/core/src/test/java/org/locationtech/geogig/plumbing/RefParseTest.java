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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.storage.RefDatabase;
import org.locationtech.geogig.storage.memory.HeapRefDatabase;

/**
 *
 */
public class RefParseTest {

    private RefDatabase refDb;

    private RefParse command;

    @Before
    public void setUp() {

        refDb = new HeapRefDatabase();
        refDb.open();

        List<Ref> allRefs = Arrays.asList(//
                testRef("refs/heads/master"), //
                testRef("refs/heads/branch1"), //
                testRef("refs/heads/v1.1"), //
                testRef("refs/tags/tag1"), //
                testRef("refs/tags/v1.1"), //
                testRef("refs/remotes/origin/master"), //
                testRef("refs/remotes/origin/branch1"), //
                testRef("refs/remotes/juan/master"), //
                testRef("refs/remotes/juan/v1.1"));

        refDb.putAll(allRefs);

        command = new RefParse();

        Context mockCommandLocator = mock(Context.class);
        when(mockCommandLocator.refDatabase()).thenReturn(refDb);
        command.setContext(mockCommandLocator);
        ResolveObjectType mockResolveObjectType = mock(ResolveObjectType.class);
        when(mockCommandLocator.command(eq(ResolveObjectType.class)))
                .thenReturn(mockResolveObjectType);

        when(mockResolveObjectType.setObjectId((ObjectId) any())).thenReturn(mockResolveObjectType);
        when(mockResolveObjectType.call()).thenReturn(TYPE.COMMIT);
    }

    private Ref testRef(String name) {
        return new Ref(name, RevObjectTestSupport.hashString(name));
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

    private void testRsolvePartial(String refSpec, String expectedResolvedName) {
        Optional<Ref> ref = command.setName(refSpec).call();
        assertTrue(ref.isPresent());
        assertEquals(expectedResolvedName, ref.get().getName());
        assertEquals(RevObjectTestSupport.hashString(expectedResolvedName),
                ref.get().getObjectId());
    }

    @Test
    public void testResolveSymbolicRef() {
        Ref target = testRef("refs/heads/branch1");
        Ref head = new SymRef(Ref.HEAD, target);
        refDb.put(target);
        refDb.put(head);

        Optional<Ref> ref = command.setName("HEAD").call();
        assertTrue(ref.isPresent());
        assertTrue(ref.get() instanceof SymRef);
        assertEquals("HEAD", ref.get().getName());
        assertEquals("refs/heads/branch1", ((SymRef) ref.get()).getTarget());
    }
}
