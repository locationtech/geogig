/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.performance;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Junit4 {@link Rule} that disables running a test if the System property
 * {@code geogig.runPerformanceTests=true} is not present.
 * <p>
 * Usage: <code>
 * <pre>
 * public class MyPerformanceTest{
 *   &#64;ClassRule
 *   public static EnablePerformanceTestRule performanceRule = new EnablePerformanceTestRule();
 *  
 *   &#64;Test
 *   public void perftest1(){
 *   ...
 *   }
 *   ...
 * }
 * </pre>
 * </code>
 */
public class EnablePerformanceTestRule implements TestRule {

    private static final Logger LOG = LoggerFactory.getLogger(EnablePerformanceTestRule.class);

    private static final String PERF_TEST_SYS_PROP = "geogig.runPerformanceTests";

    @Override
    public Statement apply(Statement base, Description description) {
        boolean enabled = Boolean.getBoolean(PERF_TEST_SYS_PROP);
        if (enabled) {
            return base;
        }

        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                LOG.info(String.format("%s ignored, run with -D%s=true to enable it.",
                        description.getClassName(), PERF_TEST_SYS_PROP));
            }
        };

    }

}
