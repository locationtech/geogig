/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.test.functional.porcelain;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

/**
 * Single cucumber test runner. Its sole purpose is to serve as an entry point for junit. Step
 * definitions and hooks are defined in their own classes so they can be reused across features.
 * 
 */
@RunWith(Cucumber.class)
@CucumberOptions(//
monochrome = true//
, plugin = { "pretty", "html:cucumber-report-porcelain" }//
, strict = true//
, glue = { "org.locationtech.geogig.cli.test.functional.general" }// where step definitions are
//, features = { "src/test/resources/org/locationtech/geogig/cli/test/functional/porcelain/Clean.feature" }//
)
public class RunPorcelainFunctionalTest {
}