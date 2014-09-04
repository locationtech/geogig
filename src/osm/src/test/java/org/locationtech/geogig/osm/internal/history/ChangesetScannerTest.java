/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal.history;

import static org.locationtech.geogig.osm.internal.history.ParsingUtils.parseDateTime;
import static org.locationtech.geogig.osm.internal.history.ParsingUtils.parseWGS84Bounds;

import java.io.InputStream;

import javax.xml.stream.XMLStreamException;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Closeables;
import com.vividsolutions.jts.geom.Envelope;

/**
 *
 */
public class ChangesetScannerTest extends Assert {

    @Test
    public void testParseChangeset() throws Exception {
        Changeset changeset = parse("1100.xml");
        assertNotNull(changeset);

        assertFalse(changeset.isOpen());
        assertEquals(1100L, changeset.getId());
        assertEquals(parseDateTime("2009-10-10T20:02:09Z"), changeset.getCreated());
        assertTrue(changeset.getClosed().isPresent());
        assertEquals(parseDateTime("2009-10-10T20:02:21Z"), changeset.getClosed().get().longValue());
        assertEquals(26L, changeset.getUserId());
        assertEquals("BMO_2009", changeset.getUserName());
        assertTrue(changeset.getComment().isPresent());
        assertEquals("second test upload of BMO data - see http://wiki.openstreetmap.org/wiki/BMO",
                changeset.getComment().get());
        assertEquals(ImmutableMap.of("created_by", "bulk_upload.py/17742 Python/2.5.2"),
                changeset.getTags());

        Envelope bounds = parseWGS84Bounds("48.4031818", "-4.4631203", "48.4058698", "-4.4589401");
        assertTrue(changeset.getWgs84Bounds().isPresent());
        assertEquals(bounds, changeset.getWgs84Bounds().get());
    }

    private Changeset parse(String resource) throws Exception {
        InputStream in = getClass().getResourceAsStream(resource);
        assertNotNull(in);
        try {
            Changeset changeset = new ChangesetScanner(in).parseNext();
            return changeset;
        } finally {
            Closeables.close(in, false);
        }
    }

}
