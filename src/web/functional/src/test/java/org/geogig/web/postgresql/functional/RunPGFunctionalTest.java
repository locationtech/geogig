/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.geogig.web.postgresql.functional;

import org.junit.runner.RunWith;
import org.locationtech.geogig.storage.postgresql.functional.PGTestUtil;

import cucumber.api.CucumberOptions;
import cucumber.api.PendingException;
import cucumber.api.junit.Cucumber;


/**
 * Single cucumber test runner. Its sole purpose is to serve as an entry point for junit. Step
 * definitions and hooks are defined in their own classes so they can be reused across features.
 * 
 * Ignored Tags:
 * <li>FileRepository - these tests are specific to file based repositories
 * <li>ShallowDepth - the MultiRepositoryProvider only serves multiple postgres repos from the same
 * set of tables, therefore it is not possible to create a shallow clone within a test due to the
 * shared object database.
 * 
 */

@RunWith(Cucumber.class)
@CucumberOptions(strict = true, plugin = { "pretty", "html:cucumber-report",
        "json:cucumber-report/cucumber.json" }, tags = { "~@FileRepository",
                "~@ShallowDepth", "~@Ignore" }, glue = {
                "org.geogig.web.functional",
                "org.geogig.web.postgresql.functional" }, features = {
                "src/test/resources/features/commands", "src/test/resources/features/repo" })
public class RunPGFunctionalTest {

    @org.junit.BeforeClass
    public static void checkPostgresTestConfig() throws PendingException {
        PGTestUtil.beforeClass();
    }

    @org.junit.AfterClass
    public static void afterClass() throws PendingException {
        PGTestUtil.afterClass();
    }}