/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.test;


import static org.junit.Assert.assertNotEquals;

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.repository.Platform;

public class TestPlatformTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    //simple test -- ensure that the platform clock is always increase (no repeated values).
    @Test
    public void testClock() throws IOException {
        Platform testPlatform = new TestPlatform(tempFolder.getRoot());

        long lastTime = 0;
        for (int t=0;t<100; t++) {
            long time = testPlatform.currentTimeMillis();
            assertNotEquals(time,lastTime);
            lastTime = time;
        }
    }
}
