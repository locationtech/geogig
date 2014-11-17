/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.cli.test.functional.remote;

import org.junit.runner.RunWith;

import cucumber.junit.Cucumber;

/**
 * Single cucumber test runner. Its sole purpose is to serve as an entry point for junit. Step
 * definitions and hooks are defined in their own classes so they can be reused across features.
 * 
 */
@RunWith(Cucumber.class)
@Cucumber.Options(//
// features = { "src/test/resources/org/locationtech/geogig/cli/test/remote/Fetch.feature" },//
monochrome = true, format = { "pretty", "html:target/cucumber-report" }, strict = true,//
// the glue option tells cucumber where else to look for step definitions
glue = { "org.locationtech.geogig.cli.test.functional.general" })
public class RunRemoteFunctionalTest {
}