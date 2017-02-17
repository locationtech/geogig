/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.test.integration;

import java.util.Collection;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.BlameException;
import org.locationtech.geogig.porcelain.BlameException.StatusCode;
import org.locationtech.geogig.porcelain.BlameOp;
import org.locationtech.geogig.porcelain.BlameReport;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ValueAndCommit;
import org.opengis.feature.Feature;

public class BlameOpTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {

    }

    @Test
    public void testBlameChangedByASingleCommit() throws Exception {
        insertAndAdd(points1);
        RevCommit firstCommit = geogig.command(CommitOp.class).call();
        String path = NodeRef.appendChild(pointsName, idP1);
        BlameReport report = geogig.command(BlameOp.class).setPath(path).call();
        Map<String, ValueAndCommit> changes = report.getChanges();
        assertEquals(3, changes.size());
        Collection<ValueAndCommit> commits = changes.values();
        for (ValueAndCommit valueAndCommit : commits) {
            assertEquals(firstCommit, valueAndCommit.commit);
        }
    }

    @Test
    public void testBlameChangedByLastCommit() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        insertAndAdd(points1_modified);
        RevCommit secondCommit = geogig.command(CommitOp.class).call();
        String path = NodeRef.appendChild(pointsName, idP1);
        BlameReport report = geogig.command(BlameOp.class).setPath(path).call();
        Map<String, ValueAndCommit> changes = report.getChanges();
        assertEquals(3, changes.size());
        Collection<ValueAndCommit> commits = changes.values();
        for (ValueAndCommit valueAndCommit : commits) {
            assertEquals(secondCommit, valueAndCommit.commit);
        }
    }

    @Test
    public void testBlameChangedByTwoCommits() throws Exception {
        insertAndAdd(points1);
        RevCommit firstCommit = geogig.command(CommitOp.class).call();
        Feature pointsModified = feature(pointsType, idP1, "StringProp1_3", new Integer(1000),
                "POINT(1 1)");
        insertAndAdd(pointsModified);
        RevCommit secondCommit = geogig.command(CommitOp.class).call();
        String path = NodeRef.appendChild(pointsName, idP1);
        BlameReport report = geogig.command(BlameOp.class).setPath(path).call();
        Map<String, ValueAndCommit> changes = report.getChanges();
        assertEquals(3, changes.size());
        assertEquals(secondCommit, changes.get("sp").commit);
        assertEquals(firstCommit, changes.get("ip").commit);
        assertEquals(firstCommit, changes.get("pp").commit);
        assertEquals(pointsModified.getProperty("sp").getValue(), changes.get("sp").value.get());
        assertEquals(points1.getProperty("ip").getValue(), changes.get("ip").value.get());
        assertEquals(points1.getProperty("pp").getValue(), changes.get("pp").value.get());

        report = geogig.command(BlameOp.class).setPath(path).setCommit(firstCommit.getId()).call();
        changes = report.getChanges();
        assertEquals(3, changes.size());
        Collection<ValueAndCommit> commits = changes.values();
        for (ValueAndCommit valueAndCommit : commits) {
            assertEquals(firstCommit, valueAndCommit.commit);
        }
    }

    @Test
    public void testBlameRemovedAndAdded() throws Exception {
        insertAndAdd(points1);
        RevCommit firstCommit = geogig.command(CommitOp.class).call();
        deleteAndAdd(points1);
        RevCommit secondCommit = geogig.command(CommitOp.class).call();
        insertAndAdd(points1);
        RevCommit thirdCommit = geogig.command(CommitOp.class).call();
        String path = NodeRef.appendChild(pointsName, idP1);
        BlameReport report = geogig.command(BlameOp.class).setPath(path).call();
        Map<String, ValueAndCommit> changes = report.getChanges();
        assertEquals(3, changes.size());
        Collection<ValueAndCommit> commits = changes.values();
        for (ValueAndCommit valueAndCommit : commits) {
            assertEquals(thirdCommit, valueAndCommit.commit);
        }

        try {
            report = geogig.command(BlameOp.class).setPath(path).setCommit(secondCommit.getId())
                    .call();
            fail();
        } catch (BlameException e) {
            assertTrue(e.statusCode == StatusCode.FEATURE_NOT_FOUND);
        }

        report = geogig.command(BlameOp.class).setPath(path).setCommit(firstCommit.getId()).call();
        changes = report.getChanges();
        assertEquals(3, changes.size());
        commits = changes.values();
        for (ValueAndCommit valueAndCommit : commits) {
            assertEquals(firstCommit, valueAndCommit.commit);
        }
    }

    @Test
    public void testBlameWithWrongFeaturePath() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        try {
            geogig.command(BlameOp.class).setPath("wrongpath").call();
            fail();
        } catch (BlameException e) {
            assertTrue(e.statusCode == StatusCode.FEATURE_NOT_FOUND);
        }

    }

    @Test
    public void testBlameWithFeatureType() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).call();
        try {
            geogig.command(BlameOp.class).setPath(pointsName).call();
            fail();
        } catch (BlameException e) {
            assertTrue(e.statusCode == StatusCode.PATH_NOT_FEATURE);
        }

    }

}