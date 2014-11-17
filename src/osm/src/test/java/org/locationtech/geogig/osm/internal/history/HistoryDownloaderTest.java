/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.osm.internal.history;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 *
 */
public class HistoryDownloaderTest extends Assert {

    private HistoryDownloader localResourcesDownloader;

    private ExecutorService executor;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File downloadFolder;

    @Before
    public void setUp() throws Exception {
        String osmAPIUrl = getClass().getResource("01_10").toExternalForm();
        long initialChangeset = 1;
        long finalChangeset = 10;

        downloadFolder = tempFolder.newFolder("downloads");

        executor = Executors.newFixedThreadPool(6);
        localResourcesDownloader = new HistoryDownloader(osmAPIUrl, downloadFolder,
                initialChangeset, finalChangeset, executor);
    }

    @Test
    public void testFetchChangesets() throws Exception {
        Iterator<Changeset> iterator = localResourcesDownloader.fetchChangesets();
        List<Changeset> changesets = Lists.newArrayList(iterator);
        assertEquals(10, changesets.size());
    }

    @Test
    public void testFetchChangesetContents() throws Exception {
        Iterator<Change> changes;
        List<Change> list;

        Iterator<Changeset> changesetsIterator = localResourcesDownloader.fetchChangesets();

        ArrayList<Changeset> changesets = Lists.newArrayList(changesetsIterator);
        assertEquals(10, changesets.size());

        changes = changesets.get(0).getChanges().get().get();
        list = Lists.newArrayList(changes);
        assertEquals(3, list.size());// see 01_10/1/download.xml
        assertTrue(list.get(0).getNode().isPresent());
        assertTrue(list.get(1).getNode().isPresent());
        assertTrue(list.get(2).getWay().isPresent());

        // 01_10/10/download.xml is empty
        changes = changesets.get(9).getChanges().get().get();
        assertFalse(changes.hasNext());

        // 01_10/5/download.xml
        changes = changesets.get(4).getChanges().get().get();
        list = Lists.newArrayList(changes);
        assertEquals(4, list.size());// see 01_10/1/download.xml
        assertTrue(list.get(0).getNode().isPresent());
        assertTrue(list.get(1).getNode().isPresent());
        assertTrue(list.get(2).getNode().isPresent());
        assertTrue(list.get(3).getWay().isPresent());
    }

    @Ignore
    @Test
    public void testFetchFailingChangesetsOnline() throws Exception {
        // String osmAPIUrl = "http://api06.dev.openstreetmap.org/api/0.6/";
        String osmAPIUrl = "http://api.openstreetmap.org/api/0.6/";
        long initialChangeset = 749;// this one gives a 500 internal server error
        long finalChangeset = 750;

        HistoryDownloader onlineDownloader = new HistoryDownloader(osmAPIUrl, downloadFolder,
                initialChangeset, finalChangeset, executor);

        Iterator<Changeset> iterator = onlineDownloader.fetchChangesets();
        assertTrue(iterator.hasNext());
        Changeset changeset = iterator.next();
        assertEquals(749, changeset.getId());

        Iterator<Change> changes;
        changes = changeset.getChanges().get().get();
        assertNotNull(changes);
        assertFalse(changes.hasNext());

        assertTrue(iterator.hasNext());
        changeset = iterator.next();
        assertEquals(750, changeset.getId());

        changes = changeset.getChanges().get().get();
        assertNotNull(changes);
        assertTrue(changes.hasNext());
    }

    @Ignore
    @Test
    public void testFetchChangesetsOnline() throws Exception {
        String osmAPIUrl = "http://api06.dev.openstreetmap.org/api/0.6/";
        // String osmAPIUrl = "http://api.openstreetmap.org/api/0.6/";
        long initialChangeset = 1;
        long finalChangeset = 30;

        HistoryDownloader onlineDownloader = new HistoryDownloader(osmAPIUrl, downloadFolder,
                initialChangeset, finalChangeset, executor);

        List<Changeset> changesets = Lists.newArrayList();
        Map<Long, List<Change>> changes = Maps.newTreeMap();

        Iterator<Changeset> it = onlineDownloader.fetchChangesets();
        while (it.hasNext()) {
            Changeset changeset = it.next();
            changesets.add(changeset);
            Iterator<Change> iterator = changeset.getChanges().get().get();
            changes.put(Long.valueOf(changeset.getId()), Lists.newArrayList(iterator));
        }

        assertEquals(30, changesets.size());
    }
}
