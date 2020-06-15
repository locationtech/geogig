/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import static org.junit.Assert.assertNotEquals;

import java.io.IOException;

import org.junit.Test;

public class DefaultPlatformTest {

    // simple test -- ensure that the platform clock is always increase (no repeated
    // values).
    @Test
    public void testClock() throws IOException {
        DefaultPlatform p1 = new DefaultPlatform();
        DefaultPlatform p2 = new DefaultPlatform();

        long lastTime = 0;
        for (int t = 0; t < 1000; t++) {
            long time1 = p1.currentTimeMillis();
            long time2 = p2.currentTimeMillis();
            assertNotEquals(time1, lastTime);
            assertNotEquals(time1, time2);
            lastTime = time2;
        }
    }
}
