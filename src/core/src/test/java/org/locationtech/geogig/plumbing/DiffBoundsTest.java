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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.Test;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.diff.DiffSummary;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.locationtech.jts.geom.Envelope;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class DiffBoundsTest extends RepositoryTestCase {

    private Feature l1Modified;

    private Feature l2Modified;

    private RevCommit points1_modified_commit;

    @Override
    protected void setUpInternal() throws Exception {
        // create one commit per feature
        ArrayList<RevCommit> commits = Lists
                .newArrayList(populate(true, points1, points3, points1_modified));
        this.points1_modified_commit = commits.get(2);

        Feature p1ModifiedAgain = feature(pointsType, idP1, "StringProp1_1a", new Integer(1001),
                "POINT(10 20)");// used to be POINT(1 2)
        insertAndAdd(p1ModifiedAgain);
        commits.add(geogig.command(CommitOp.class).call());

        points1B_modified = feature(pointsType, idP1, "StringProp1B_1a", new Integer(2000),
                "POINT(10 220)");
        insertAndAdd(points1B_modified);
        commits.add(geogig.command(CommitOp.class).call());

        l1Modified = feature(linesType, idL1, "StringProp2_1", new Integer(1000),
                "LINESTRING (1 1, -2 -2)");// used to be LINESTRING (1 1, 2 2)

        l2Modified = feature(linesType, idL2, "StringProp2_2", new Integer(2000),
                "LINESTRING (3 3, 4 4)");// used to be LINESTRING (3 3, 4 4)
    }

    @Test
    public void testDiffBetweenDifferentTrees() {
        String oldRefSpec = "HEAD~3";
        String newRefSpec = "HEAD";

        DiffSummary<Envelope, Envelope> diffBounds = geogig.command(DiffBounds.class)
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

        DiffSummary<Envelope, Envelope> diffBounds = geogig.command(DiffBounds.class)
                .setOldVersion(oldRefSpec).setNewVersion(newRefSpec).call();
        assertTrue(diffBounds.getLeft().isNull());
        assertTrue(diffBounds.getRight().isNull());
        assertTrue(diffBounds.getMergedResult().get().isNull());
    }

    @Test
    public void testPathFiltering() throws Exception {
        insertAndAdd(l1Modified);
        geogig.command(CommitOp.class).call();
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

        List<String> filter = ImmutableList.<String> copyOf(pathFilters);

        DiffSummary<Envelope, Envelope> result = geogig.command(DiffBounds.class)//
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
