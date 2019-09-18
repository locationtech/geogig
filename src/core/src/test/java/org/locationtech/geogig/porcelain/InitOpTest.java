/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.porcelain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.locationtech.geogig.model.Ref.HEAD;
import static org.locationtech.geogig.model.Ref.MASTER;
import static org.locationtech.geogig.model.Ref.STAGE_HEAD;
import static org.locationtech.geogig.model.Ref.WORK_HEAD;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.SymRef;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.UpdateRefs;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryFinder;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapRefDatabase;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 *
 */
public class InitOpTest {

    private Platform platform;

    private Context context;

    private InitOp init;

    private HeapRefDatabase refdb;

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    private File workingDir;

    private Repository mockRepo;

    private RefParse mockRefParse;

    private UpdateRefs mockUpdateRefs;

    private ObjectDatabase objectDatabase;

    private RepositoryResolver mockResolver;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() throws IOException, RepositoryConnectionException {
        context = mock(Context.class);
        refdb = new HeapRefDatabase();
        refdb.open();
        when(context.refDatabase()).thenReturn(refdb);

        mockRefParse = mock(RefParse.class);
        when(mockRefParse.setName(anyString())).thenReturn(mockRefParse);

        mockUpdateRefs = new UpdateRefs();
        mockUpdateRefs.setContext(context);
        when(context.command(eq(UpdateRefs.class))).thenReturn(mockUpdateRefs);

        platform = mock(Platform.class);
        when(context.platform()).thenReturn(platform);

        workingDir = tempFolder.getRoot();
        Hints hints = new Hints();
        hints.set(Hints.REPOSITORY_URL, workingDir.getAbsoluteFile().toURI());
        when(context.hints()).thenReturn(hints);
        init = new InitOp();
        init.setContext(context);
        mockResolver = mock(RepositoryResolver.class);
        RepositoryFinder mockFinder = spy(new RepositoryFinder());
        init.setRepositoryFinder(mockFinder);
        doReturn(mockResolver).when(mockFinder).lookup(any());

        mockRepo = mock(Repository.class);
        objectDatabase = new HeapObjectDatabase();
        when(mockRepo.context()).thenReturn(context);
        when(context.objectDatabase()).thenReturn(objectDatabase);

        Mockito.doAnswer(new Answer<Void>() {
            public @Override Void answer(InvocationOnMock invocation) throws Throwable {
                objectDatabase.open();
                return null;
            }
        }).when(mockRepo).open();

        when(platform.pwd()).thenReturn(workingDir);

    }

    @Test
    public void testNullWorkingDir() {
        when(platform.pwd()).thenReturn(null);
        exception.expect(IllegalStateException.class);
        init.call();
        when(platform.pwd()).thenReturn(workingDir);
    }

    @Test
    public void testCreateNewRepo() throws Exception {
        when(context.repository()).thenReturn(mockRepo);
        Optional<Ref> absent = Optional.empty();
        when(mockRefParse.call()).thenReturn(absent);
        when(mockResolver.repoExists(any())).thenReturn(false);

        final URI expectedURI = new File(workingDir, ".geogig").toURI();
        Repository created = init.call();

        assertSame(mockRepo, created);
        verify(mockResolver, times(1)).initialize(eq(expectedURI));
        verify(context, times(1)).repository();

        assertEquals("init: repository initialization", mockUpdateRefs.getReason().orElse(null));
        assertEquals(new Ref(WORK_HEAD, RevTree.EMPTY_TREE_ID), refdb.get(WORK_HEAD).orElse(null));
        assertEquals(new Ref(STAGE_HEAD, RevTree.EMPTY_TREE_ID),
                refdb.get(STAGE_HEAD).orElse(null));
        Ref master = new Ref(MASTER, ObjectId.NULL);
        assertEquals(master, refdb.get(MASTER).orElse(null));
        assertEquals(new SymRef(HEAD, master), refdb.get(HEAD).orElse(null));
        assertEquals(RevTree.EMPTY, objectDatabase.get(RevTree.EMPTY_TREE_ID));
    }

    @Test
    public void testReinitializeExistingRepo() throws Exception {
        when(context.repository()).thenReturn(mockRepo);
        Optional<Ref> absent = Optional.empty();
        when(mockRefParse.call()).thenReturn(absent);
        when(mockResolver.repoExists(any())).thenReturn(true);

        final URI expectedURI = new File(workingDir, ".geogig").toURI();

        Repository created = init.call();
        assertSame(mockRepo, created);
        verify(mockResolver, times(1)).initialize(eq(expectedURI));

        Ref master = new Ref(MASTER, RevObjectTestSupport.hashString("hash me"));
        refdb.put(master);

        refdb.delete(Arrays.asList(HEAD, WORK_HEAD, STAGE_HEAD));
        objectDatabase.delete(RevTree.EMPTY_TREE_ID);
        assertNotNull(init.call());

        assertEquals("init: repository re-initialization", mockUpdateRefs.getReason().orElse(null));
        assertEquals(master, refdb.get(MASTER).orElse(null));
        assertEquals(new SymRef(HEAD, master), refdb.get(HEAD).orElse(null));
        assertEquals(new Ref(WORK_HEAD, master.getObjectId()), refdb.get(WORK_HEAD).orElse(null));
        assertEquals(new Ref(STAGE_HEAD, master.getObjectId()), refdb.get(STAGE_HEAD).orElse(null));
        assertEquals(RevTree.EMPTY, objectDatabase.get(RevTree.EMPTY_TREE_ID));
    }

    @Test
    public void testReinitializeExistingRepoFromInsideASubdirectory() throws Exception {
        testCreateNewRepo();

        File subDir = new File(workingDir, ".geogig/subdir1/subdir2");
        assertTrue(subDir.mkdirs());

        when(platform.pwd()).thenReturn(subDir);

        assertTrue(ResolveGeogigURI.lookup(platform.pwd()).isPresent());

        when(mockResolver.repoExists(any())).thenReturn(true);
        assertNotNull(init.call());
        verify(platform, atLeastOnce()).pwd();
    }
}
