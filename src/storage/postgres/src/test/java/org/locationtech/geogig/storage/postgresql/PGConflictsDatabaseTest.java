/* Copyright (c) 2016 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.api.ObjectId.NULL;
import static org.locationtech.geogig.api.ObjectId.forString;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.locationtech.geogig.api.plumbing.merge.Conflict;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

@RunWith(MockitoJUnitRunner.class)
public class PGConflictsDatabaseTest {

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @Rule
    public PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(getClass().getSimpleName());

    private PGConflictsDatabase conflicts;

    private DataSource dataSource;

    @Mock
    private DataSource mockSource;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Connection mockConnection;

    private PGConflictsDatabase mockSourceConflicts;

    Conflict c1 = new Conflict("Rivers/1", NULL, forString("ours"), forString("theirs"));

    Conflict c2 = new Conflict("Rivers/2", forString("ancestor"), forString("ours2"),
            forString("theirs2"));

    Conflict c3 = new Conflict("Rivers/3", forString("ancestor"), forString("ours3"),
            forString("theirs3"));

    @Before
    public void before() throws SQLException {
        Environment environment = testConfig.getEnvironment();
        PGStorage.createNewRepo(environment);
        dataSource = PGStorage.newDataSource(environment);

        String conflictsTable = environment.getTables().conflicts();
        String repositoryId = environment.getRepositoryId();
        conflicts = new PGConflictsDatabase(dataSource, conflictsTable, repositoryId);

        when(mockSource.getConnection()).thenReturn(mockConnection);
        mockSourceConflicts = new PGConflictsDatabase(mockSource, conflictsTable, repositoryId);
    }

    @After
    public void after() {
        if (dataSource != null) {
            PGStorage.closeDataSource(dataSource);
        }
    }

    @Test
    public void testAddGetNullNamespace() {
        conflicts.addConflict(null, c1);
        assertEquals(c1, conflicts.getConflict(null, c1.getPath()).get());

        conflicts.addConflict(null, c2);
        conflicts.addConflict(null, c3);

        assertEquals(c2, conflicts.getConflict(null, c2.getPath()).get());
        assertEquals(c3, conflicts.getConflict(null, c3.getPath()).get());

        assertFalse(conflicts.getConflict(null, "not/a/conflict").isPresent());
    }

    @Test
    public void testAddGetNamespace() {
        final String ns = UUID.randomUUID().toString();

        conflicts.addConflict(ns, c1);
        assertEquals(c1, conflicts.getConflict(ns, c1.getPath()).get());
        assertFalse(conflicts.getConflict(null, c1.getPath()).isPresent());

        conflicts.addConflict(ns, c2);
        conflicts.addConflict(ns, c3);

        assertEquals(c2, conflicts.getConflict(ns, c2.getPath()).get());
        assertEquals(c3, conflicts.getConflict(ns, c3.getPath()).get());

        assertFalse(conflicts.getConflict(ns, "not/a/conflict").isPresent());
        assertFalse(conflicts.getConflict(null, c2.getPath()).isPresent());
        assertFalse(conflicts.getConflict(null, c3.getPath()).isPresent());
    }

    @Test
    public void testAddConflicts() {
        final String ns = null;
        Iterable<Conflict> list = ImmutableList.of(c1, c2, c3);
        conflicts.addConflicts(ns, list);
        assertEquals(c1, conflicts.getConflict(ns, c1.getPath()).get());
        assertEquals(c2, conflicts.getConflict(ns, c2.getPath()).get());
        assertEquals(c3, conflicts.getConflict(ns, c3.getPath()).get());
    }

    @Test
    public void testAddConflictsNS() {
        final String ns = UUID.randomUUID().toString();
        Iterable<Conflict> list = ImmutableList.of(c1, c2, c3);
        conflicts.addConflicts(ns, list);
        assertEquals(c1, conflicts.getConflict(ns, c1.getPath()).get());
        assertEquals(c2, conflicts.getConflict(ns, c2.getPath()).get());
        assertEquals(c3, conflicts.getConflict(ns, c3.getPath()).get());
    }

    @Test
    public void testHasConflicts() {
        final String ns = UUID.randomUUID().toString();
        assertFalse(conflicts.hasConflicts(null));
        assertFalse(conflicts.hasConflicts(ns));

        conflicts.addConflict(null, c1);
        conflicts.addConflict(ns, c2);
        conflicts.addConflict(ns, c3);

        assertTrue(conflicts.hasConflicts(null));
        assertTrue(conflicts.hasConflicts(ns));
        assertFalse(conflicts.hasConflicts(UUID.randomUUID().toString()));
    }

    @Test
    public void testGetConflicts() {
        final String ns = UUID.randomUUID().toString();
        conflicts.addConflict(null, c1);
        conflicts.addConflict(null, c3);

        List<Conflict> res;
        res = conflicts.getConflicts(null, null);
        assertTrue(res.contains(c1));
        assertTrue(res.contains(c3));

        res = conflicts.getConflicts(null, "3");
        assertFalse(res.contains(c1));
        assertTrue(res.contains(c3));

        res = conflicts.getConflicts(null, "Rivers");
        assertTrue(res.contains(c1));
        assertTrue(res.contains(c3));

        conflicts.addConflict(ns, c1);
        conflicts.addConflict(ns, c2);
        conflicts.addConflict(ns, c3);

        res = conflicts.getConflicts(ns, "Rivers");
        assertTrue(res.contains(c1));
        assertTrue(res.contains(c2));
        assertTrue(res.contains(c3));

        res = conflicts.getConflicts(ns, "Rivers/2");
        assertFalse(res.contains(c1));
        assertTrue(res.contains(c2));
        assertFalse(res.contains(c3));
    }

    @Test
    public void testRemoveConflict() {
        final String ns = UUID.randomUUID().toString();
        conflicts.addConflict(null, c1);
        conflicts.addConflict(ns, c2);
        conflicts.addConflict(ns, c3);
        conflicts.addConflict(null, c3);

        conflicts.removeConflict(ns, c1.getPath());
        assertEquals(c1, conflicts.getConflict(null, c1.getPath()).get());
        conflicts.removeConflict(null, c1.getPath());
        assertFalse(conflicts.getConflict(null, c1.getPath()).isPresent());
        assertFalse(conflicts.getConflict(ns, c1.getPath()).isPresent());

        conflicts.removeConflict(null, c2.getPath());
        assertEquals(c2, conflicts.getConflict(ns, c2.getPath()).get());
        conflicts.removeConflict(ns, c2.getPath());
        assertFalse(conflicts.getConflict(null, c2.getPath()).isPresent());
        assertFalse(conflicts.getConflict(ns, c2.getPath()).isPresent());

        assertTrue(conflicts.hasConflicts(null));
        assertTrue(conflicts.hasConflicts(ns));
        conflicts.removeConflict(null, c3.getPath());
        assertFalse(conflicts.hasConflicts(null));
        conflicts.removeConflict(ns, c3.getPath());
        assertFalse(conflicts.hasConflicts(ns));
    }

    @Test
    public void testRemoveConflicts() {
        final String ns = UUID.randomUUID().toString();
        conflicts.addConflict(null, c1);
        conflicts.addConflict(ns, c2);
        conflicts.addConflict(ns, c3);
        conflicts.addConflict(null, c3);

        assertTrue(conflicts.hasConflicts(null));
        assertTrue(conflicts.hasConflicts(ns));

        conflicts.removeConflicts(null);
        assertFalse(conflicts.hasConflicts(null));
        assertTrue(conflicts.hasConflicts(ns));

        conflicts.addConflict(null, c1);

        conflicts.removeConflicts(ns);
        assertTrue(conflicts.hasConflicts(null));
        assertFalse(conflicts.hasConflicts(ns));
    }

    @Test
    public void addConflictConnectException() throws SQLException {
        when(mockSource.getConnection()).thenThrow(new SQLException("connection error"));
        expected.expect(RuntimeException.class);
        expected.expectMessage("connection error");
        mockSourceConflicts.addConflict(null, c1);
    }

    @Test
    public void addConflictExecutionException() throws SQLException {
        when(mockSource.getConnection().prepareStatement(anyString()))
                .thenThrow(new SQLException("propagate this message"));
        try {
            mockSourceConflicts.addConflict(null, c1);
            fail("expected RTE");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("propagate this message"));
        }
        verify(mockConnection, times(1)).setAutoCommit(eq(false));
        verify(mockConnection, times(1)).rollback();
        verify(mockConnection, times(1)).setAutoCommit(eq(true));
    }

    @Test
    public void getConflictException() throws SQLException {
        when(mockSource.getConnection().prepareStatement(anyString()).executeQuery())
                .thenThrow(new SQLException("propagate this message"));
        try {
            mockSourceConflicts.getConflict(null, c1.getPath());
            fail("expected RTE");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("propagate this message"));
        }
        verify(mockConnection, times(0)).setAutoCommit(eq(false));
    }

    @Test
    public void removeConflictConnectException() throws SQLException {
        when(mockSource.getConnection()).thenThrow(new SQLException("connection error"));
        expected.expect(RuntimeException.class);
        expected.expectMessage("connection error");
        mockSourceConflicts.removeConflict(null, c1.getPath());
    }

    @Test
    public void removeConflictExecutionException() throws SQLException {
        when(mockSource.getConnection().prepareStatement(anyString()))
                .thenThrow(new SQLException("propagate this message"));
        try {
            mockSourceConflicts.removeConflict(null, c1.getPath());
            fail("expected RTE");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("propagate this message"));
        }
        verify(mockConnection, times(1)).setAutoCommit(eq(false));
        verify(mockConnection, times(1)).rollback();
        verify(mockConnection, times(1)).setAutoCommit(eq(true));
    }

    @Test
    public void removeConflictsConnectException() throws SQLException {
        when(mockSource.getConnection()).thenThrow(new SQLException("connection error"));
        expected.expect(RuntimeException.class);
        expected.expectMessage("connection error");
        mockSourceConflicts.removeConflicts(null);
    }

    @Test
    public void removeConflictsExecutionException() throws SQLException {
        when(mockSource.getConnection().prepareStatement(anyString()))
                .thenThrow(new SQLException("propagate this message"));
        try {
            mockSourceConflicts.removeConflicts(null);
            fail("expected RTE");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("propagate this message"));
        }
        verify(mockConnection, times(1)).setAutoCommit(eq(false));
        verify(mockConnection, times(1)).rollback();
        verify(mockConnection, times(1)).setAutoCommit(eq(true));
    }
}
