/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.geogig.web.functional;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

/**
 * Single cucumber test runner. Its sole purpose is to serve as an entry point for junit. Step
 * definitions and hooks are defined in their own classes so they can be reused across features.
 * 
 */

@RunWith(Cucumber.class)
@CucumberOptions(strict = true, plugin = { "pretty", "html:cucumber-report",
        "json:cucumber-report/cucumber.json" }, tags = { "~@Ignore" }, glue = {
                "org.geogig.web.functional" }, features = { "src/test/resources/features/commands",
                        "src/test/resources/features/repo" })
public class RunFunctionalTest {
}