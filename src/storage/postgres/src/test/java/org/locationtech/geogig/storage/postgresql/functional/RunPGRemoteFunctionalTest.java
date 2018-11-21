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

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.PendingException;
import cucumber.api.junit.Cucumber;

/**
 * Single cucumber test runner. Its sole purpose is to serve as an entry point for junit. Step
 * definitions and hooks are defined in their own classes so they can be reused across features.
 * 
 */
@RunWith(Cucumber.class)
@CucumberOptions(//
        plugin = { "pretty", "html:cucumber-report-general" }//
        , strict = true//
        // the glue option tells cucumber where else to look for step definitions
        , glue = { "org.locationtech.geogig.storage.postgresql.functional",
                "org.locationtech.geogig.cli.test.functional" } //
        , features = { "../../cli/remoting/src/test/resources/features/remote" })
public class RunPGRemoteFunctionalTest {

    @org.junit.BeforeClass
    public static void checkPostgresTestConfig() throws PendingException {
        PGTestUtil.beforeClass();
    }

    @org.junit.AfterClass
    public static void afterClass() throws PendingException {
        PGTestUtil.afterClass();
    }
}