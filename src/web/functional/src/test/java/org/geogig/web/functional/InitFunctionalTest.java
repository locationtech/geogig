/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.geogig.web.functional;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

/**
 * Single cucumber test runner. Its sole purpose is to serve as an entry point for junit. Step
 * definitions and hooks are defined in their own classes so they can be reused across features.
 *
 * This entry point is specific for INIT command behavior for scenarios where the stand-alone Web
 * API server has slightly different behavior than the GeoServer plugin. Currently, the only
 * difference is the message in the 409 Conflict response when the repository already exists. Since
 * the plugin has a RepositoryManager, it will check to see if the name is already in use. If the
 * name is already in use, the message will indicate this. The stand-alone server does not check
 * this ahead of time and only issues a 409 Conflict if the repo already exists.
 */

@RunWith(Cucumber.class)
@CucumberOptions(strict = true, plugin = { "pretty", "html:cucumber-report",
        "json:cucumber-report/cucumber.json" }, tags = { "~@Ignore" }, glue = {
                "org.geogig.web.functional" }, features = { "src/test/resources/features/init" })
public class InitFunctionalTest {

}
