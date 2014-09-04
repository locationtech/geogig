/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.test.functional.general;

import org.junit.runner.RunWith;

import cucumber.junit.Cucumber;

/**
 * Single cucumber test runner. Its sole purpose is to serve as an entry point for junit. Step
 * definitions and hooks are defined in their own classes so they can be reused across features.
 * 
 */
// use features=... to specify one or more specific features to execute
// @Cucumber.Options(features = { "src/test/resources/org/locationtech/geogig/cli/test/functional/Branch.feature"
// }, monochrome = true, format = {
// "pretty", "html:target/cucumber-report" }, strict = true)
@Cucumber.Options(monochrome = true, format = { "pretty", "html:target/cucumber-report" }, strict = true)
@RunWith(Cucumber.class)
public class RunFunctionalTest {
}