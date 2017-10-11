/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import java.io.IOException;
import java.util.Map;

import org.junit.Test;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

public class INIBlobTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
    }

    @Test
    public void testParseBlob() throws Exception {

        StringBuilder filterFileBuilder = new StringBuilder("");
        filterFileBuilder.append("[default]\n");
        filterFileBuilder.append("type = CQL\n");
        filterFileBuilder.append("filter = BBOX(pp,30, -125, 40, -110,'EPSG:4326')\n");
        filterFileBuilder.append("[Cities]\n");
        filterFileBuilder.append("type = CQL\n");
        filterFileBuilder.append("filter = BBOX(pp,33, -125, 40, -110,'EPSG:4326')\n");

        final String filterFile = filterFileBuilder.toString();

        geogig.getRepository().blobStore().putBlob(Blobs.SPARSE_FILTER_BLOB_KEY,
                filterFile.getBytes());

        INIBlob test = new INIBlob() {

            public byte[] bytes = filterFile.getBytes();

            @Override
            public byte[] iniBytes() throws IOException {
                // TODO Auto-generated method stub
                return this.bytes;
            }

            @Override
            public void setBytes(byte[] bytes) throws IOException {
                this.bytes = bytes;
            }
        };

        Map<String, String> entries = test.getAll();
        assertEquals(4, entries.size());
        assertEquals("CQL", entries.get("Cities.type"));
        assertEquals("CQL", entries.get("default.type"));
        assertEquals("BBOX(pp,33, -125, 40, -110,'EPSG:4326')", entries.get("Cities.filter"));
        assertEquals("BBOX(pp,30, -125, 40, -110,'EPSG:4326')", entries.get("default.filter"));

        test.set("newFilter", "type", "CQL");
        test.set("newFilter", "filter", "BBOX(pp,36, -125, 40, -110,'EPSG:4326')");

        entries = test.getAll();
        assertEquals(6, entries.size());
        assertEquals("CQL", entries.get("newFilter.type"));
        assertEquals("BBOX(pp,36, -125, 40, -110,'EPSG:4326')", entries.get("newFilter.filter"));
    }

}
