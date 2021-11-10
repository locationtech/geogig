/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Jillian Crossley (Cornell University) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Test;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.diff.DiffSummary;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.locationtech.jts.geom.Envelope;

public class DiffBoundsTest extends RepositoryTestCase {

    private Feature l1Modified;

    private Feature l2Modified;

    private RevCommit points1_modified_commit;

    protected @Override void setUpInternal() throws Exception {
        // create one commit per feature
        List<RevCommit> commits = new ArrayList<>(
                populate(true, points1, points3, points1_modified));
        this.points1_modified_commit = commits.get(2);

        Feature p1ModifiedAgain = feature(pointsType, idP1, "StringProp1_1a", Integer.valueOf(1001),
                "POINT(10 20)");// used to be POINT(1 2)
        insertAndAdd(p1ModifiedAgain);
        commits.add(repo.command(CommitOp.class).call());

        points1B_modified = feature(pointsType, idP1, "StringProp1B_1a", Integer.valueOf(2000),
                "POINT(10 220)");
        insertAndAdd(points1B_modified);
        commits.add(repo.command(CommitOp.class).call());

        l1Modified = feature(linesType, idL1, "StringProp2_1", Integer.valueOf(1000),
                "LINESTRING (1 1, -2 -2)");// used to be LINESTRING (1 1, 2 2)

        l2Modified = feature(linesType, idL2, "StringProp2_2", Integer.valueOf(2000),
                "LINESTRING (3 3, 4 4)");// used to be LINESTRING (3 3, 4 4)
    }

    @Test
    public void testDiffBetweenDifferentTrees() {
        String oldRefSpec = "HEAD~3";
        String newRefSpec = "HEAD";

        DiffSummary<Envelope, Envelope> diffBounds = repo.command(DiffBounds.class)
                .setOldVersion(oldRefSpec).setNewVersion(newRefSpec).call();

        Envelope bounds = diffBounds.getMergedResult().get();
        assertEquals(1.0, bounds.getMinX(), 0.0);
        assertEquals(1.0, bounds.getMinY(), 0.0);
        assertEquals(10.0, bounds.getMaxX(), 0.0);
        assertEquals(220.0, bounds.getMaxY(), 0.0);
    }

    @Test
    public void testDiffBetweenIdenticalTrees() {
        String oldRefSpec = "HEAD";
        String newRefSpec = "HEAD";

        DiffSummary<Envelope, Envelope> diffBounds = repo.command(DiffBounds.class)
                .setOldVersion(oldRefSpec).setNewVersion(newRefSpec).call();
        assertTrue(diffBounds.getLeft().isNull());
        assertTrue(diffBounds.getRight().isNull());
        assertTrue(diffBounds.getMergedResult().get().isNull());
    }

    @Test
    public void testPathFiltering() throws Exception {
        insertAndAdd(l1Modified);
        repo.command(CommitOp.class).call();
        insert(l2Modified);

        testPathFiltering("HEAD~3", "HEAD", l1Modified.getDefaultGeometryBounds(), linesName);
        testPathFiltering("HEAD", "WORK_HEAD", l2Modified.getDefaultGeometryBounds(), linesName);
        testPathFiltering("HEAD~3", "HEAD~2", null, linesName);
        testPathFiltering("HEAD~3", "HEAD~2", null, linesName);

        String head = points1_modified_commit.getId().toString();

        Envelope expected = points1.getDefaultGeometryBounds();
        expected.expandToInclude(points1_modified.getDefaultGeometryBounds());

        testPathFiltering(head + "^", head, expected, pointsName);
        testPathFiltering(head + "^", head, null, linesName);
        testPathFiltering("HEAD^", "HEAD", null, pointsName);
    }

    private void testPathFiltering(String oldVersion, String newVersion,
            @Nullable Envelope expected, @Nullable String... pathFilters) {

        List<String> filter = Arrays.asList(pathFilters);

        DiffSummary<Envelope, Envelope> result = repo.command(DiffBounds.class)//
                .setOldVersion(oldVersion)//
                .setNewVersion(newVersion)//
                .setPathFilters(filter)//
                .call();

        Envelope actual = result.getMergedResult().get();
        if (null == expected) {
            assertTrue(actual.isNull());
        } else {
            assertEquals(expected, actual);
        }
    }

}
