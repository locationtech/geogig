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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.RefParse;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.plumbing.UpdateRef;
import org.locationtech.geogig.plumbing.UpdateSymRef;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryFinder;
import org.locationtech.geogig.repository.RepositoryResolver;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.locationtech.geogig.storage.memory.HeapObjectDatabase;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 *
 */
public class InitOpTest {

    private Platform platform;

    private Context injector;

    private InitOp init;

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    private File workingDir;

    private Repository mockRepo;

    private RefParse mockRefParse;

    private UpdateRef mockUpdateRef;

    private UpdateSymRef mockUpdateSymRef;

    private ObjectDatabase objectDatabase;

    private RepositoryResolver mockResolver;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() throws IOException, RepositoryConnectionException {
        injector = mock(Context.class);

        mockRefParse = mock(RefParse.class);
        when(mockRefParse.setName(anyString())).thenReturn(mockRefParse);

        mockUpdateRef = mock(UpdateRef.class);
        when(mockUpdateRef.setName(anyString())).thenReturn(mockUpdateRef);
        when(mockUpdateRef.setDelete(anyBoolean())).thenReturn(mockUpdateRef);
        when(mockUpdateRef.setNewValue((ObjectId) any())).thenReturn(mockUpdateRef);
        when(mockUpdateRef.setOldValue((ObjectId) any())).thenReturn(mockUpdateRef);
        when(mockUpdateRef.setReason(anyString())).thenReturn(mockUpdateRef);

        mockUpdateSymRef = mock(UpdateSymRef.class);
        when(mockUpdateSymRef.setName(anyString())).thenReturn(mockUpdateSymRef);
        when(mockUpdateSymRef.setDelete(anyBoolean())).thenReturn(mockUpdateSymRef);
        when(mockUpdateSymRef.setNewValue(anyString())).thenReturn(mockUpdateSymRef);
        when(mockUpdateSymRef.setOldValue(anyString())).thenReturn(mockUpdateSymRef);
        when(mockUpdateSymRef.setReason(anyString())).thenReturn(mockUpdateSymRef);

        when(injector.command(eq(RefParse.class))).thenReturn(mockRefParse);
        when(injector.command(eq(UpdateRef.class))).thenReturn(mockUpdateRef);
        when(injector.command(eq(UpdateSymRef.class))).thenReturn(mockUpdateSymRef);

        platform = mock(Platform.class);
        when(injector.platform()).thenReturn(platform);

        workingDir = tempFolder.getRoot();
        Hints hints = new Hints();
        hints.set(Hints.REPOSITORY_URL, workingDir.getAbsoluteFile().toURI());
        init = new InitOp(hints);
        init.setContext(injector);
        mockResolver = mock(RepositoryResolver.class);
        RepositoryFinder mockFinder = spy(new RepositoryFinder());
        init.setRepositoryFinder(mockFinder);
        doReturn(mockResolver).when(mockFinder).lookup(any());

        mockRepo = mock(Repository.class);
        objectDatabase = new HeapObjectDatabase();
        when(mockRepo.objectDatabase()).thenReturn(objectDatabase);

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
        when(injector.repository()).thenReturn(mockRepo);
        Optional<Ref> absent = Optional.empty();
        when(mockRefParse.call()).thenReturn(absent);
        when(mockResolver.repoExists(any())).thenReturn(false);

        final URI expectedURI = new File(workingDir, ".geogig").toURI();
        Repository created = init.call();

        assertSame(mockRepo, created);
        verify(mockResolver, times(1)).initialize(eq(expectedURI), any());
        verify(injector, times(1)).repository();

        verify(mockUpdateRef, times(1)).setName(eq(Ref.MASTER));
        verify(mockUpdateRef, times(1)).setName(eq(Ref.WORK_HEAD));
        verify(mockUpdateRef, times(1)).setName(eq(Ref.STAGE_HEAD));
        verify(mockUpdateRef, times(1)).setNewValue(eq(ObjectId.NULL));
        verify(mockUpdateRef, times(2)).setNewValue(eq(RevTree.EMPTY_TREE_ID));
        verify(mockUpdateRef, times(3)).setReason(anyString());
        verify(mockUpdateRef, times(3)).call();

        verify(mockUpdateSymRef, times(1)).setName(eq(Ref.HEAD));
        verify(mockUpdateSymRef, times(1)).setNewValue(eq(Ref.MASTER));
        verify(mockUpdateSymRef, times(1)).call();

        assertEquals(RevTree.EMPTY, objectDatabase.get(RevTree.EMPTY_TREE_ID));
    }

    @Test
    @Ignore
    public void testReinitializeExistingRepo() throws Exception {
        when(injector.repository()).thenReturn(mockRepo);
        Optional<Ref> absent = Optional.empty();
        when(mockRefParse.call()).thenReturn(absent);
        when(mockResolver.repoExists(any())).thenReturn(true);

        final URI expectedURI = new File(workingDir, ".geogig").toURI();

        Repository created = init.call();
        assertSame(mockRepo, created);
        verify(mockResolver, times(1)).initialize(eq(expectedURI), any());
        verify(mockUpdateRef, times(0)).call();
        verify(mockUpdateSymRef, times(0)).call();

        Ref master = new Ref(Ref.MASTER, RevObjectTestSupport.hashString("hash me"));

        when(mockRefParse.call()).thenReturn(Optional.of(master));

        Context injector = mock(Context.class);
        when(injector.command(eq(RefParse.class))).thenReturn(mockRefParse);
        when(injector.platform()).thenReturn(platform);
        when(injector.repository()).thenReturn(mockRepo);
        init.setContext(injector);

        assertNotNull(init.call());

        verify(injector, never()).command(eq(UpdateRef.class));
        verify(injector, never()).command(eq(UpdateSymRef.class));

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
