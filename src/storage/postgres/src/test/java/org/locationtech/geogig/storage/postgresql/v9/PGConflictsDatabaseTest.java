/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.v9;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.model.ObjectId.NULL;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.repository.Conflict;
import org.locationtech.geogig.repository.impl.RepositoryBusyException;
import org.locationtech.geogig.storage.postgresql.config.Environment;
import org.locationtech.geogig.storage.postgresql.config.TableNames;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Complements {@link PGConflictsDatabaseConformanceTest} with tests specific to this implementation
 * that can use mock objects so {@link PGConflictsDatabaseConformanceTest} can run faster for cases
 * where the actual connection to the database is not needed.
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class PGConflictsDatabaseTest {

    private static final Conflict c1 = new Conflict("Rivers/1", NULL,
            RevObjectTestSupport.hashString("ours"), RevObjectTestSupport.hashString("theirs"));

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @Mock
    private Environment mockEnv;

    private PGConflictsDatabase mockSourceConflicts;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Connection mockConnection;

    @Before
    public void beforeMocks() throws SQLException {
        when(mockEnv.getRepositoryName()).thenReturn("mock-repo");
        TableNames mockTables = new TableNames();
        when(mockEnv.getTables()).thenReturn(mockTables);
        when(mockEnv.getConnection()).thenReturn(mockConnection);
        mockSourceConflicts = new PGConflictsDatabase(mockEnv);
    }

    @Test
    public void addConflictConnectException() throws SQLException {
        when(mockEnv.getConnection()).thenThrow(
                new RepositoryBusyException("No available connections to the repository."));
        expected.expect(RepositoryBusyException.class);
        expected.expectMessage("No available connections to the repository");
        mockSourceConflicts.addConflict(null, c1);
    }

    @Test
    public void addConflictExecutionException() throws SQLException {
        when(mockEnv.getConnection().prepareStatement(anyString()))
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
        when(mockEnv.getConnection().prepareStatement(anyString()).executeQuery())
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
        when(mockEnv.getConnection()).thenThrow(
                new RepositoryBusyException("No available connections to the repository."));
        expected.expect(RepositoryBusyException.class);
        expected.expectMessage("No available connections to the repository");
        mockSourceConflicts.removeConflict(null, c1.getPath());
    }

    @Test
    public void removeConflictExecutionException() throws SQLException {
        when(mockEnv.getConnection().prepareStatement(anyString()))
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
        when(mockEnv.getConnection()).thenThrow(
                new RepositoryBusyException("No available connections to the repository."));
        expected.expect(RepositoryBusyException.class);
        expected.expectMessage("No available connections to the repository");
        mockSourceConflicts.removeConflicts(null);
    }

    @Test
    public void removeConflictsExecutionException() throws SQLException {
        when(mockEnv.getConnection().prepareStatement(anyString()))
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
