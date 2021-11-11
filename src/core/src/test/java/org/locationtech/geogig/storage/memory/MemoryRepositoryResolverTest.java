/* Copyright (c) 2020 Gabriel Roldan
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Test;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.storage.memory.MemoryRepositoryResolver.MemoryContext;

public class MemoryRepositoryResolverTest {

    private String context1 = "context1";

    private String context2 = "context2";

    private MemoryRepositoryResolver resolver = new MemoryRepositoryResolver();

    public @After void after() {
        MemoryRepositoryResolver.removeContext(context1);
        MemoryRepositoryResolver.removeContext(context2);
        MemoryRepositoryResolver.removeContext("c1");
        MemoryRepositoryResolver.removeContext("c2");
    }

    public @Test void testRemoveContext() {
        MemoryContext c1 = MemoryRepositoryResolver.getOrCreateContext(context1);
        MemoryContext c2 = MemoryRepositoryResolver.getOrCreateContext(context2);

        assertNotNull(c1);
        assertSame(c1, MemoryRepositoryResolver.getOrCreateContext(context1));

        assertNotNull(c2);
        MemoryRepositoryResolver.removeContext(context1);
        assertFalse(MemoryRepositoryResolver.getContext(context1).isPresent());
        assertSame(c2, MemoryRepositoryResolver.getOrCreateContext(context2));
    }

    public @Test void testCanHandleURIScheme() {
        assertFalse(resolver.canHandleURIScheme(null));
        assertFalse(resolver.canHandleURIScheme(""));
        assertFalse(resolver.canHandleURIScheme("file"));
        assertFalse(resolver.canHandleURIScheme("Memory"));
        assertTrue(resolver.canHandleURIScheme("memory"));
    }

    public @Test void testCanHandle() {
        assertFalse(resolver.canHandle(URI.create("file:/tmp")));
        assertFalse(resolver.canHandle(URI.create("/")));
        assertTrue(resolver.canHandle(URI.create("memory://contextName")));
        assertTrue(resolver.canHandle(URI.create("memory://contextName/")));
        assertTrue(resolver.canHandle(URI.create("memory://contextName/#repo_name")));
        assertTrue(resolver.canHandle(URI.create("memory://contextName/path/to/parent#repo_name")));
    }

    public @Test void testCreateRootURI() {
        assertEquals(URI.create("memory://contextname/"), resolver.createRootURI(" context name "));
        assertEquals(URI.create("memory://c1/"), resolver.createRootURI("c1"));
    }

    public @Test void testBuildRepoURI() {
        URI root = URI.create("memory://c1/");
        assertEquals(URI.create("memory://c1/#repo%20name"),
                resolver.buildRepoURI(root, "repo name"));

        root = URI.create("memory://c1/path/to/parent/");
        assertEquals(URI.create("memory://c1/path/to/parent/#repo%20name"),
                resolver.buildRepoURI(root, "repo name"));

        root = URI.create("memory://c1/path/to/parent");
        assertEquals(URI.create("memory://c1/path/to/parent/#repo%20name"),
                resolver.buildRepoURI(root, "repo name"));

        root = URI.create("memory://c1/path/to/parent/../..");
        assertEquals(URI.create("memory://c1/path/#repo%20name"),
                resolver.buildRepoURI(root, "repo name"));
    }

    public @Test void testGetRootURI() {
        assertEquals(URI.create("memory://c1/path/to/parent/"),
                resolver.getRootURI(URI.create("memory://c1/path/to/parent/#repo%20name")));

        assertEquals(URI.create("memory://c1/"),
                resolver.getRootURI(URI.create("memory://c1/path/to/parent/../../..#repo%20name")));
    }

    public @Test void testInitialize() {
        URI root1 = URI.create("memory://" + this.context1 + "/path/to/parent/");
        URI root2 = URI.create("memory://" + this.context2 + "/path/to/");
        String name = "repository name";

        URI repo1 = resolver.buildRepoURI(root1, name);
        URI repo2 = resolver.buildRepoURI(root2, name);

        assertFalse(resolver.repoExists(repo1));
        assertFalse(resolver.repoExists(repo2));

        resolver.initialize(repo1);
        resolver.initialize(repo2);

        assertTrue(MemoryRepositoryResolver.getContext(context1).get().contains(repo1));
        assertFalse(MemoryRepositoryResolver.getContext(context2).get().contains(repo1));

        assertFalse(MemoryRepositoryResolver.getContext(context1).get().contains(repo2));
        assertTrue(MemoryRepositoryResolver.getContext(context2).get().contains(repo2));
    }

    public @Test void testRepoExists() throws Exception {
        URI root1 = URI.create("memory://c1/path/to/parent/");
        URI root2 = URI.create("memory://c1/path/to/");
        String name = "repository name";

        assertFalse(resolver.repoExists(resolver.buildRepoURI(root1, name)));
        resolver.initialize(resolver.buildRepoURI(root1, name));
        resolver.initialize(resolver.buildRepoURI(root2, name));

        assertTrue(resolver.repoExists(resolver.buildRepoURI(root1, name)));
        assertTrue(resolver.repoExists(resolver.buildRepoURI(root2, name)));

        resolver.delete(resolver.buildRepoURI(root2, name));
        assertTrue(resolver.repoExists(resolver.buildRepoURI(root1, name)));
        assertFalse(resolver.repoExists(resolver.buildRepoURI(root2, name)));
    }

    public @Test void testListRepoNamesUnderRootURI() {
        URI root1 = URI.create("memory://context1/");
        URI root2 = URI.create("memory://context1/parent/");
        URI root3 = URI.create("memory://context2/parent/path/");

        resolver.initialize(resolver.buildRepoURI(root1, "r1"));
        resolver.initialize(resolver.buildRepoURI(root1, "r2"));
        resolver.initialize(resolver.buildRepoURI(root1, "r3"));

        resolver.initialize(resolver.buildRepoURI(root2, "r1"));
        resolver.initialize(resolver.buildRepoURI(root2, "r4"));

        resolver.initialize(resolver.buildRepoURI(root3, "r1"));
        resolver.initialize(resolver.buildRepoURI(root3, "r5"));

        assertEquals(Set.of("r1", "r2", "r3"),
                resolver.listRepoNamesUnderRootURI(root1).stream().collect(Collectors.toSet()));
        assertEquals(Set.of("r1", "r4"),
                resolver.listRepoNamesUnderRootURI(root2).stream().collect(Collectors.toSet()));
        assertEquals(Set.of("r1", "r5"),
                resolver.listRepoNamesUnderRootURI(root3).stream().collect(Collectors.toSet()));
    }

    public @Test void testGetName() {
        URI repoURI = resolver.buildRepoURI(resolver.createRootURI(context1), "repo 1");
        assertTrue(repoURI.toString().endsWith("#repo%201"));
        assertEquals("repo 1", resolver.getName(repoURI));
    }

    public @Test void testOpen() throws RepositoryConnectionException {
        URI repoURI = URI.create("memory://context2/parent/path/#repo1");
        try {
            resolver.open(repoURI);
            fail("RepositoryConnectionException");
        } catch (RepositoryConnectionException expected) {
            assertTrue(true);
        }
        resolver.initialize(repoURI);
        Repository repo = resolver.open(repoURI);
        assertNotNull(repo);
        assertEquals(repoURI, repo.getLocation());
        assertTrue(repo.isOpen());

        Repository repo2 = resolver.open(repoURI, Hints.readOnly());
        assertNotNull(repo2);
        assertNotSame(repo, repo2);
        assertEquals(repoURI, repo2.getLocation());
        assertTrue(repo2.isOpen());
        assertTrue(Hints.isRepoReadOnly(repo2.context().hints()));
        assertTrue(repo2.context().configDatabase().isReadOnly());
        assertTrue(repo2.context().conflictsDatabase().isReadOnly());
        assertTrue(repo2.context().indexDatabase().isReadOnly());
        assertTrue(repo2.context().objectDatabase().isReadOnly());
        assertTrue(repo2.context().refDatabase().isReadOnly());
    }

    public @Test void testDelete() throws Exception {
        URI root1 = URI.create("memory://c1/path/to/parent/");
        URI root2 = URI.create("memory://c1/path/to/");
        String name = "repository name";
        resolver.initialize(resolver.buildRepoURI(root1, name));
        resolver.initialize(resolver.buildRepoURI(root2, name));

        assertTrue(resolver.repoExists(resolver.buildRepoURI(root1, name)));
        assertTrue(resolver.repoExists(resolver.buildRepoURI(root2, name)));

        resolver.delete(resolver.buildRepoURI(root2, name));
        assertTrue(resolver.repoExists(resolver.buildRepoURI(root1, name)));
        assertFalse(resolver.repoExists(resolver.buildRepoURI(root2, name)));

        resolver.delete(resolver.buildRepoURI(root1, name));
        assertFalse(resolver.repoExists(resolver.buildRepoURI(root1, name)));
        assertFalse(resolver.repoExists(resolver.buildRepoURI(root2, name)));
    }
}
