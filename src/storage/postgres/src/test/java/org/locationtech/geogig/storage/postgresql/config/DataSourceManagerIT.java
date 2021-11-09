/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.config;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class DataSourceManagerIT {

    public @Rule PGTemporaryTestConfig testConfig = new PGTemporaryTestConfig(
            getClass().getSimpleName());

    private DataSourceManager dsm;

    public @Before void before() {
        dsm = new DataSourceManager();
        dsm.driverVersionVerified = false;
    }

    public @After void after() {
        dsm.releaseAll();
    }

    public @Test void testAcquireRelease() {
        ConnectionConfig config = testConfig.getEnvironment().getConnectionConfig();
        DataSource ds1 = dsm.acquire(config.getKey());
        assertNotNull(ds1);
        DataSource ds2 = dsm.acquire(config.getKey());
        assertSame(ds1, ds2);

        dsm.release(ds1);
        dsm.release(ds2);

        DataSource ds3 = dsm.acquire(config.getKey());
        assertNotNull(ds1);
        assertNotSame(ds1, ds3);
        dsm.release(ds3);
    }

    public @Test void testVerifyDriverVersion() {
        assertEquals(42, dsm.getDriverMajorVersion());
        assertTrue(dsm.verifyDriverVersion());
    }

    public @Test void testGetPostgresJars() {
        List<String> jarlocations = dsm.getPostgresJars();
        assertNotNull(jarlocations);
        assertEquals("There are more than one postgres jar in the classpath: " + jarlocations, 1,
                jarlocations.size());
    }

    public @Test void testVerifyDriverVersionInvalid() {
        dsm = spy(new DataSourceManager());
        doReturn(9).when(dsm).getDriverMajorVersion();
        doReturn(11).when(dsm).getDriverMinorVersion();

        List<String> mockjars = Arrays.asList("/usr/local/lib/postgres911.jar",
                "postgres42.jar");
        doReturn(mockjars).when(dsm).getPostgresJars();

        assertFalse(dsm.verifyDriverVersion());

        ArgumentCaptor<String> logErrCaptor = ArgumentCaptor.forClass(String.class);
        verify(dsm).logError(logErrCaptor.capture());

        String logError = logErrCaptor.getValue();
        assertNotNull(logError);
        assertThat(logError, containsString(
                "GeoGig PostgreSQL support requires PostgreSQL JDBC Driver version 42.1.1 and above"));
        assertThat(logError,
                containsString("org.postgresql.Driver '9.11' was loaded by the classloader"));
        assertThat(logError, containsString(
                "The following jar files contain the class org.postgresql.Driver: [/usr/local/lib/postgres911.jar, postgres42.jar]"));
    }

    public @Test void testAcquireDriverVersionInvalid() {
        dsm = spy(new DataSourceManager());
        doReturn(9).when(dsm).getDriverMajorVersion();
        doReturn(11).when(dsm).getDriverMinorVersion();

        Exception e = assertThrows(IllegalStateException.class,
                () -> dsm.acquire(testConfig.getEnvironment().getConnectionConfig().getKey()));
        assertThat(e.getMessage(),
                containsString("PostgreSQL JDBC Driver version not supported by GeoGig: 9.11"));
    }
}
