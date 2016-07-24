/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Optional;

public class OSMAplyDiffOpTest extends RepositoryTestCase {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        repo.configDatabase().put("user.name", "groldan");
        repo.configDatabase().put("user.email", "groldan@boundlessgeo.com");
    }

    @Test
    public void testApplyChangeset() throws Exception {
        String filename = getClass().getResource("nodes_for_changeset2.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        long unstaged = geogig.getRepository().workingTree().countUnstaged("node").count();
        assertTrue(unstaged > 0);
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:node/2059114068").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("WORK_HEAD:node/507464865")
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());

        String changesetFilename = getClass().getResource("changeset.xml").getFile();
        geogig.command(OSMApplyDiffOp.class).setDiffFile(new File(changesetFilename)).call();
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("WORK_HEAD:node/2059114068")
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("WORK_HEAD:node/507464865")
                .call(RevFeature.class);
        assertTrue(revFeature.isPresent());

    }

    @Test
    public void testApplyChangesetWithMissingNode() throws Exception {
        String filename = getClass().getResource("nodes_for_changeset2.xml").getFile();
        File file = new File(filename);
        geogig.command(OSMImportOp.class).setDataSource(file.getAbsolutePath()).call();
        long unstaged = geogig.getRepository().workingTree().countUnstaged("node").count();
        assertTrue(unstaged > 0);
        Optional<RevFeature> revFeature = geogig.command(RevObjectParse.class)
                .setRefSpec("WORK_HEAD:node/2059114068").call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("WORK_HEAD:node/269237867")
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());

        String changesetFilename = getClass().getResource("changeset_missing_nodes.xml").getFile();
        OSMReport report = geogig.command(OSMApplyDiffOp.class)
                .setDiffFile(new File(changesetFilename)).call().get();
        assertEquals(1, report.getUnpprocessedCount());
        assertEquals(4, report.getCount());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("WORK_HEAD:way/51502277")
                .call(RevFeature.class);
        assertTrue(revFeature.isPresent());
        revFeature = geogig.command(RevObjectParse.class).setRefSpec("WORK_HEAD:way/31347480")
                .call(RevFeature.class);
        assertFalse(revFeature.isPresent());

    }
}
