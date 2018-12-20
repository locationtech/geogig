/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

import org.junit.Test;

public class HintsTest {

    @Test
    public void testHints() {
        Hints hints = new Hints();
        assertTrue(hints.getAll().isEmpty());
        assertFalse(hints.get("not present").isPresent());
        hints.set("key", "myValue");
        assertEquals("myValue", hints.get("key").get());
        assertFalse(hints.getBoolean("key"));
        hints.set("key2", true);
        assertTrue(hints.getBoolean("key2"));
    }

    @Test
    public void testReadOnly() {
        Hints hints = Hints.readOnly();
        assertTrue(hints.getBoolean(Hints.OBJECTS_READ_ONLY));
        assertTrue(hints.getBoolean(Hints.REMOTES_READ_ONLY));
    }

    @Test
    public void testReadWrite() {
        Hints hints = Hints.readWrite();
        assertFalse(hints.getBoolean(Hints.OBJECTS_READ_ONLY));
        assertFalse(hints.getBoolean(Hints.REMOTES_READ_ONLY));
    }

    @Test
    public void testUri() throws URISyntaxException {
        Hints hints = new Hints();
        assertFalse(hints.get(Hints.REPOSITORY_URL).isPresent());
        URI repoURI = new URI("repoURI");
        hints.uri(repoURI);
        assertEquals(repoURI, hints.get(Hints.REPOSITORY_URL).get());
    }

    @Test
    public void testPlatform() {
        @SuppressWarnings("serial")
        Platform platform = new Platform() {
            @Override
            public File pwd() {
                return null;
            }

            @Override
            public void setWorkingDir(File workingDir) {
            }

            @Override
            public String whoami() {
                return null;
            }

            @Override
            public long currentTimeMillis() {
                return 0;
            }

            @Override
            public long nanoTime() {
                return 0;
            }

            @Override
            public File getUserHome() {
                return null;
            }

            @Override
            public int timeZoneOffset(long timeStamp) {
                return 0;
            }

            @Override
            public int availableProcessors() {
                return 0;
            }
        };

        Hints hints = new Hints();
        assertFalse(hints.get(Hints.PLATFORM).isPresent());
        hints.platform(platform);
        assertEquals(platform, hints.get(Hints.PLATFORM).get());
    }

    @Test
    public void testToString() {
        Hints hints = new Hints();
        hints.set("key1", "value1");
        hints.set("key2", "value2");
        assertEquals(hints.getAll().toString(), hints.toString());
    }

    @Test
    public void testEquals() {
        Hints hints1 = new Hints();
        hints1.set("key1", "value1");
        hints1.set("key2", "value2");

        Hints hints2 = new Hints();

        assertTrue(hints1.equals(hints1));
        assertFalse(hints1.equals(hints2));
        assertFalse(hints1.equals("hints"));
        assertFalse(hints2.equals(hints1));

        hints2.set("key2", "value2");
        hints2.set("key1", "value1");

        assertTrue(hints1.equals(hints2));
        assertTrue(hints2.equals(hints1));
    }
}
