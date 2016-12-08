package org.locationtech.geogig.cli;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

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
        Throwable e = null;
        try {
            returned = console.checkAnsiSupported(System.out, "windows 10");
        } catch (Throwable er) {
            // catch attempt to create WindowsAnsiOutput
            e = er;
        }
        assertTrue(returned || e instanceof UnsatisfiedLinkError);
    }

    @Test
    public void testWindows7Detection() throws Throwable {
        Console console = new Console();
        assertFalse(console.checkAnsiSupported(System.out, "windows 7"));
    }
}
