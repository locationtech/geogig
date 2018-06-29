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
 *
 */
@RunWith(Cucumber.class)
@CucumberOptions(strict = true, plugin = { "pretty", "html:cucumber-report",
        "json:cucumber-report/cucumber.json" }, tags = { "~@Ignore" }, glue = {
                "org.geogig.web.functional" }, features = {
                        "src/test/resources/features/resolvers" })
public class MissingResolversTest {

}
