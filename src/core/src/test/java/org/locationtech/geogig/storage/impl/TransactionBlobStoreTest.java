/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.io.ByteStreams;

public abstract class TransactionBlobStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private TransactionBlobStore blobStore;

    @Before
    public void before() {
        this.blobStore = createBlobStore(tmp.getRoot());
    }

    protected abstract TransactionBlobStore createBlobStore(File currentDirectory);

    @Test
    public void testEmpty() {
        assertFalse(blobStore.getBlob("MERGE_HEAD").isPresent());
        assertFalse(blobStore.getBlobAsStream("MERGE_HEAD").isPresent());
        assertFalse(blobStore.getBlob("mytransaction", "MERGE_HEAD").isPresent());
        assertFalse(blobStore.getBlob("mytransaction", "osm/someblob").isPresent());
        assertFalse(blobStore.getBlobAsStream("mytransaction", "osm/someblob").isPresent());
    }

    @Test
    public void testPutGetBlob() {
        blobStore.putBlob("MERGE_HEAD", "some contents\nsecond line".getBytes(Charsets.UTF_8));
        Optional<byte[]> blob = blobStore.getBlob("MERGE_HEAD");
        assertNotNull(blob);
        assertTrue(blob.isPresent());
        assertEquals("some contents\nsecond line", new String(blob.get(), Charsets.UTF_8));
    }

    @Test
    public void testPutGetBlobAsStream() throws IOException {
        InputStream blobArg = new ByteArrayInputStream(
                "some contents\nsecond line".getBytes(Charsets.UTF_8));
        blobStore.putBlob("MERGE_HEAD", blobArg);

        Optional<InputStream> blob = blobStore.getBlobAsStream("MERGE_HEAD");
        assertNotNull(blob);
        assertTrue(blob.isPresent());
        byte[] bytes;
        try (InputStream in = blob.get()) {
            bytes = ByteStreams.toByteArray(in);
        }
        assertEquals("some contents\nsecond line", new String(bytes, Charsets.UTF_8));
    }

    @Test
    public void testPutGetBlobNamespace() {
        String contents = "some contents\nsecond line";
        testPutGetNamespace("some_tx_id", "MERGE_HEAD", contents);
        testPutGetNamespace("some_tx_id", "osm/MERGE_HEAD", contents);
        testPutGetNamespace("some_tx_id", "osm/MERGE_HEAD", "");
    }

    private void testPutGetNamespace(String namespace, String path, String contents) {
        byte[] blobArg = contents.getBytes(Charsets.UTF_8);
        blobStore.putBlob(namespace, path, blobArg);

        Optional<byte[]> blob = blobStore.getBlob(namespace, path);
        assertNotNull(blob);
        assertTrue(blob.isPresent());

        byte[] bytes = blob.get();

        assertEquals(contents, new String(bytes, Charsets.UTF_8));
    }

    @Test
    public void testPutGetBlobAsStreamNamespace() throws IOException {
        String contents = "some contents\nsecond line";
        testPutGetAsStreamNamespace("some_tx_id", "MERGE_HEAD", contents);
        testPutGetAsStreamNamespace("some_tx_id", "osm/MERGE_HEAD", contents);
        testPutGetAsStreamNamespace("some_tx_id", "osm/MERGE_HEAD", "");
    }

    private void testPutGetAsStreamNamespace(String namespace, String path, String contents)
            throws IOException {
        InputStream blobArg = new ByteArrayInputStream(contents.getBytes(Charsets.UTF_8));

        blobStore.putBlob(namespace, path, blobArg);

        Optional<InputStream> blob = blobStore.getBlobAsStream(namespace, path);
        assertNotNull(blob);
        assertTrue(blob.isPresent());

        byte[] bytes;
        try (InputStream in = blob.get()) {
            bytes = ByteStreams.toByteArray(in);
        }
        assertEquals(contents, new String(bytes, Charsets.UTF_8));
    }

    @Test
    public void testRemove() {
        blobStore.putBlob("MERGE_HEAD", "some contents".getBytes(Charsets.UTF_8));
        assertTrue(blobStore.getBlob("MERGE_HEAD").isPresent());
        blobStore.removeBlob("MERGE_HEAD");
        assertFalse(blobStore.getBlob("MERGE_HEAD").isPresent());

        blobStore.putBlob("tx1", "MERGE_HEAD", "some contents".getBytes(Charsets.UTF_8));
        assertTrue(blobStore.getBlob("tx1", "MERGE_HEAD").isPresent());
        blobStore.removeBlob("tx1", "MERGE_HEAD");
        assertFalse(blobStore.getBlob("tx1", "MERGE_HEAD").isPresent());

        blobStore.putBlob("tx1", "osm/MERGE_HEAD", "some contents".getBytes(Charsets.UTF_8));
        assertTrue(blobStore.getBlob("tx1", "osm/MERGE_HEAD").isPresent());
        blobStore.removeBlob("tx1", "osm/MERGE_HEAD");
        assertFalse(blobStore.getBlob("tx1", "osm/MERGE_HEAD").isPresent());
    }

    @Test
    public void testRemoveBlobs() {
        blobStore.putBlob("MERGE_HEAD", "some contents".getBytes(Charsets.UTF_8));
        blobStore.putBlob("tx1", "MERGE_HEAD", "some contents".getBytes(Charsets.UTF_8));
        blobStore.putBlob("tx1", "osm/MERGE_HEAD", "some contents".getBytes(Charsets.UTF_8));

        blobStore.putBlob("tx2", "MERGE_HEAD", "some contents".getBytes(Charsets.UTF_8));
        blobStore.putBlob("tx2", "osm/MERGE_HEAD", "some contents".getBytes(Charsets.UTF_8));

        assertTrue(blobStore.getBlob("MERGE_HEAD").isPresent());
        assertTrue(blobStore.getBlob("tx1", "MERGE_HEAD").isPresent());
        assertTrue(blobStore.getBlob("tx1", "osm/MERGE_HEAD").isPresent());
        assertTrue(blobStore.getBlob("tx2", "MERGE_HEAD").isPresent());
        assertTrue(blobStore.getBlob("tx2", "osm/MERGE_HEAD").isPresent());

        blobStore.removeBlobs("tx1");
        assertTrue(blobStore.getBlob("MERGE_HEAD").isPresent());
        assertFalse(blobStore.getBlob("tx1", "MERGE_HEAD").isPresent());
        assertFalse(blobStore.getBlob("tx1", "osm/MERGE_HEAD").isPresent());
        assertTrue(blobStore.getBlob("tx2", "MERGE_HEAD").isPresent());
        assertTrue(blobStore.getBlob("tx2", "osm/MERGE_HEAD").isPresent());

        blobStore.removeBlobs("tx2");
        assertTrue(blobStore.getBlob("MERGE_HEAD").isPresent());
        assertFalse(blobStore.getBlob("tx2", "MERGE_HEAD").isPresent());
        assertFalse(blobStore.getBlob("tx2", "osm/MERGE_HEAD").isPresent());

    }
}
