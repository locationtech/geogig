/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.performance.je;

import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.test.integration.je.JETestContextBuilder;
import org.locationtech.geogig.test.performance.LogOpPerformanceTest;

public class JELogOpPerformanceTest extends LogOpPerformanceTest {
    @Override
    protected Context createInjector() {
        Hints hints = new Hints().uri(repositoryDirectory.toURI()).platform(createPlatform());
        return new JETestContextBuilder().build(hints);
    }
}
