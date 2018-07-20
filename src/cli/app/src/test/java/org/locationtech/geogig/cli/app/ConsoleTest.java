/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Morgan Thompson - initial implementation
 */
package org.locationtech.geogig.cli.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.cli.Console;

public class ConsoleTest {

    @Before
    public final void setUpInternal() {
    }

    @Test
    public void forceAnsiTest() {
        Console console = new Console();
        console.setForceAnsi(true);
        assertTrue(console.isAnsiSupported());
        console.setForceAnsi(false);
        assertFalse(console.isAnsiSupported());
    }

    @Test
    public void testWindows10Detection() {
        Console console = new Console();
        boolean returned = false;
        try {
            returned = console.checkAnsiSupported(System.out, "windows 10");
        } catch (Throwable er) {
            returned = true;
        }
        assertTrue(returned);
    }

    @Test
    public void testWindows7Detection() throws Throwable {
        Console console = new Console();
        assertFalse(console.checkAnsiSupported(System.out, "windows 7"));
    }
}
