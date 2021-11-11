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

import java.util.Objects;

import org.junit.internal.AssumptionViolatedException;
import org.locationtech.geogig.base.Preconditions;
import org.locationtech.geogig.storage.postgresql.config.PGTemporaryTestConfig;
import org.locationtech.geogig.storage.postgresql.config.PGTestDataSourceProvider;

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
        Objects.requireNonNull(perTestSuiteDataSourceProvider);
        Preconditions.checkState(perTestSuiteDataSourceProvider.isEnabled());
        return new PGTemporaryTestConfig(repoName, perTestSuiteDataSourceProvider);
    }
}
