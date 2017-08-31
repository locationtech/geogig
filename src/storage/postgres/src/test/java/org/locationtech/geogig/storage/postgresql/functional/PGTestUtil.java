/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.functional;

import org.junit.internal.AssumptionViolatedException;
import org.locationtech.geogig.storage.postgresql.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.PGTestDataSourceProvider;

import com.google.common.base.Preconditions;

import cucumber.api.PendingException;

public class PGTestUtil {

    private static PGTestDataSourceProvider perTestSuiteDataSourceProvider;

    public static void beforeClass() throws PendingException {
        perTestSuiteDataSourceProvider = new PGTestDataSourceProvider();
        try {
            perTestSuiteDataSourceProvider.before();
        } catch (AssumptionViolatedException disabled) {
            System.err.println(
                    "#######################################################################################################");
            System.err.println(
                    "#                                                                                                     #");
            System.err.println(
                    "#     PostgreSQL functional tests disabled. Configure $HOME/.geogig-pg-backend-tests.properties       #");
            System.err.println(
                    "#                                                                                                     #");
            System.err.println(
                    "#######################################################################################################");
            throw new PendingException();
        }
    }

    public static void afterClass() {
        perTestSuiteDataSourceProvider.after();
    }

    public static PGTemporaryTestConfig newTestConfig(String repoName) {
        Preconditions.checkNotNull(perTestSuiteDataSourceProvider);
        Preconditions.checkState(perTestSuiteDataSourceProvider.isEnabled());
        return new PGTemporaryTestConfig(repoName, perTestSuiteDataSourceProvider);
    }
}
