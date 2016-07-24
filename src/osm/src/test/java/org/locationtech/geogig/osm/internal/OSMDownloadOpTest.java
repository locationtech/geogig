/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.osm.internal;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.osm.internal.log.OSMLogEntry;
import org.locationtech.geogig.osm.internal.log.ReadOSMLogEntries;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.porcelain.NothingToCommitException;
import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.Blobs;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Optional;

public class OSMDownloadOpTest extends RepositoryTestCase {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUpInternal() throws Exception {
        repo.configDatabase().put("user.name", "groldan");
        repo.configDatabase().put("user.email", "groldan@boundlessgeo.com");
    }

    @Ignore
    @Test
    public void testDownloadNodes() throws Exception {
        String filename = OSMImportOp.class.getResource("nodes_overpass_filter.txt").getFile();
        File filterFile = new File(filename);
        OSMDownloadOp download = geogig.command(OSMDownloadOp.class);
        download.setFilterFile(filterFile).setOsmAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT).call();
        Optional<Node> tree = geogig.getRepository().getRootTreeChild("node");
        assertTrue(tree.isPresent());
        List<OSMLogEntry> entries = geogig.command(ReadOSMLogEntries.class).call();
        assertFalse(entries.isEmpty());
        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        assertTrue(log.hasNext());
    }

    @Ignore
    @Test
    public void testDownloadEmptyFilter() throws Exception {
        String filename = OSMImportOp.class.getResource("empty_filter.txt").getFile();
        File filterFile = new File(filename);
        try {
            OSMDownloadOp download = geogig.command(OSMDownloadOp.class);
            download.setFilterFile(filterFile).setOsmAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT).call();
            fail();
        } catch (IllegalArgumentException e) {
        }

    }

    @Ignore
    @Test
    public void testDowloadNodesWithDestinationFile() throws Exception {
        String filename = OSMImportOp.class.getResource("nodes_overpass_filter.txt").getFile();
        File filterFile = new File(filename);
        File downloadFile = File.createTempFile("osm-geogig", ".xml");
        OSMDownloadOp download = geogig.command(OSMDownloadOp.class);
        download.setFilterFile(filterFile).setSaveFile(downloadFile)
                .setOsmAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT).call();
        Optional<Node> tree = geogig.getRepository().getRootTreeChild("node");
        assertTrue(tree.isPresent());
        List<OSMLogEntry> entries = geogig.command(ReadOSMLogEntries.class).call();
        assertFalse(entries.isEmpty());
        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        assertTrue(log.hasNext());
    }

    @Ignore
    @Test
    public void testDownaloadWays() throws Exception {
        String filename = OSMImportOp.class.getResource("ways_overpass_filter.txt").getFile();
        File filterFile = new File(filename);
        OSMDownloadOp download = geogig.command(OSMDownloadOp.class);
        download.setFilterFile(filterFile).setOsmAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT).call();
        Optional<Node> tree = geogig.getRepository().getRootTreeChild("node");
        assertTrue(tree.isPresent());
        tree = geogig.getRepository().getRootTreeChild("way");
        assertTrue(tree.isPresent());
        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        assertTrue(log.hasNext());
    }

    @Ignore
    @Test
    public void testDownloadWaysWithoutNodes() throws Exception {
        String filename = OSMImportOp.class.getResource("ways_no_nodes_overpass_filter.txt")
                .getFile();
        File filterFile = new File(filename);
        try {
            OSMDownloadOp download = geogig.command(OSMDownloadOp.class);
            download.setFilterFile(filterFile).setOsmAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT).call();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("The specified filter did not return any element"));
        }

    }

    @Ignore
    @Test
    public void testDownloadWithBBox() throws Exception {
        OSMDownloadOp download = geogig.command(OSMDownloadOp.class);
        download.setBbox(Arrays.asList("50.79", "7.19", "50.8", "7.20"))
                .setOsmAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT).call();
        Optional<Node> tree = geogig.getRepository().getRootTreeChild("way");
        assertTrue(tree.isPresent());
        Iterator<RevCommit> log = geogig.command(LogOp.class).call();
        assertTrue(log.hasNext());
    }

    @Ignore
    @Test
    public void testDownloadWithBBoxAndAlternativeUrl() throws Exception {
        String url = "http://api.openstreetmap.fr/oapi/interpreter/";
        OSMDownloadOp download = geogig.command(OSMDownloadOp.class);
        download.setBbox(Arrays.asList("50.79", "7.19", "50.8", "7.20")).setOsmAPIUrl(url).call();
        Optional<Node> tree = geogig.getRepository().getRootTreeChild("way");
        assertTrue(tree.isPresent());
    }

    @Ignore
    @Test
    public void testDownloadWithBBoxAndMapping() throws Exception {
        String mappingFilename = OSMMapOp.class.getResource("mapping.json").getFile();
        File mappingFile = new File(mappingFilename);
        OSMDownloadOp download = geogig.command(OSMDownloadOp.class);
        download.setMappingFile(mappingFile)
                .setBbox(Arrays.asList("50.79", "7.19", "50.8", "7.20"))
                .setOsmAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT).call();
        Optional<Node> tree = geogig.getRepository().getRootTreeChild("way");
        assertTrue(tree.isPresent());
        tree = geogig.getRepository().getRootTreeChild("onewaystreets");
        assertTrue(tree.isPresent());
        // check it has created mapping log files
        BlobStore blobStore = getRepository().context().blobStore();
        Optional<String> blob = Blobs.getBlobAsString(blobStore, "osm/map/onewaystreets");
        assertTrue(blob.isPresent());
        ObjectId workingHead = getRepository().workingTree().getTree().getId();
        blob = Blobs.getBlobAsString(blobStore, "osm/map/" + workingHead);
        assertTrue(blob.isPresent());
    }

    @Test
    public void testImportWithWrongBBox() throws Exception {
        try {
            OSMDownloadOp download = geogig.command(OSMDownloadOp.class);
            download.setBbox(Arrays.asList("asdads", "7.19", "50.8", "7.20"))
                    .setOsmAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT).call();
            fail();
        } catch (IllegalStateException e) {
        }
    }

    @Test
    public void testImportWithWrongUrl() throws Exception {
        try {
            OSMDownloadOp download = geogig.command(OSMDownloadOp.class);
            download.setBbox(Arrays.asList("50.79", "7.19", "50.8", "7.20"))
                    .setOsmAPIUrl("http://invalidurl.com").call();
            fail();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Did you try to use a standard OSM server instead?"));
        }
    }

    @Ignore
    @Test
    public void testUpdate() throws Exception {
        String filename = OSMImportOp.class.getResource("fire_station_filter.txt").getFile();
        File filterFile = new File(filename);
        OSMDownloadOp download = geogig.command(OSMDownloadOp.class);
        download.setFilterFile(filterFile).setOsmAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT).call();
        Optional<Node> tree = geogig.getRepository().getRootTreeChild("node");
        assertTrue(tree.isPresent());
        tree = geogig.getRepository().getRootTreeChild("way");
        assertTrue(tree.isPresent());
        List<OSMLogEntry> entries = geogig.command(ReadOSMLogEntries.class).call();
        assertFalse(entries.isEmpty());
        OSMUpdateOp update = geogig.command(OSMUpdateOp.class);
        try {
            update.setAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT).call();
        } catch (NothingToCommitException e) {
            // No new data
        }
    }

    @Ignore
    @Test
    public void testUpdatewithBBox() throws Exception {
        OSMDownloadOp download = geogig.command(OSMDownloadOp.class);
        download.setBbox(Arrays.asList("50.79", "7.19", "50.8", "7.20"))
                .setOsmAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT).call();
        Optional<Node> tree = geogig.getRepository().getRootTreeChild("node");
        assertTrue(tree.isPresent());
        tree = geogig.getRepository().getRootTreeChild("way");
        assertTrue(tree.isPresent());
        List<OSMLogEntry> entries = geogig.command(ReadOSMLogEntries.class).call();
        assertFalse(entries.isEmpty());
        OSMUpdateOp update = geogig.command(OSMUpdateOp.class);
        try {
            update.setAPIUrl(OSMUtils.DEFAULT_API_ENDPOINT).call();
        } catch (NothingToCommitException e) {
            // No new data
        }
    }

}
