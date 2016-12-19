/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.test.functional;

import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

/**
 * Single cucumber test runner. Its sole purpose is to serve as an entry point for junit. Step
 * definitions and hooks are defined in their own classes so they can be reused across features.
 * 
 */
@CucumberOptions(//
        plugin = { "pretty", "html:cucumber-report-general" }//
        , strict = true//
        , features = { "src/test/resources/features/general" })
@RunWith(Cucumber.class)
public class RunGenralFunctionalTest {
}