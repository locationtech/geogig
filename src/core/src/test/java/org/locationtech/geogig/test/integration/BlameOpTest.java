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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Map;

import org.junit.Test;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.porcelain.BlameException;
import org.locationtech.geogig.porcelain.BlameException.StatusCode;
import org.locationtech.geogig.porcelain.BlameOp;
import org.locationtech.geogig.porcelain.BlameReport;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.porcelain.ValueAndCommit;

public class BlameOpTest extends RepositoryTestCase {

    protected @Override void setUpInternal() throws Exception {

    }

    @Test
    public void testBlameChangedByASingleCommit() throws Exception {
        insertAndAdd(points1);
        RevCommit firstCommit = repo.command(CommitOp.class).call();
        String path = NodeRef.appendChild(pointsName, idP1);
        BlameReport report = repo.command(BlameOp.class).setPath(path).call();
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
        repo.command(CommitOp.class).call();
        insertAndAdd(points1_modified);
        RevCommit secondCommit = repo.command(CommitOp.class).call();
        String path = NodeRef.appendChild(pointsName, idP1);
        BlameReport report = repo.command(BlameOp.class).setPath(path).call();
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
        RevCommit firstCommit = repo.command(CommitOp.class).call();
        Feature pointsModified = feature(pointsType, idP1, "StringProp1_3", Integer.valueOf(1000),
                "POINT(1 1)");
        insertAndAdd(pointsModified);
        RevCommit secondCommit = repo.command(CommitOp.class).call();
        String path = NodeRef.appendChild(pointsName, idP1);
        BlameReport report = repo.command(BlameOp.class).setPath(path).call();
        Map<String, ValueAndCommit> changes = report.getChanges();
        assertEquals(3, changes.size());
        assertEquals(secondCommit, changes.get("sp").commit);
        assertEquals(firstCommit, changes.get("ip").commit);
        assertEquals(firstCommit, changes.get("pp").commit);
        assertEquals(pointsModified.getAttribute("sp"), changes.get("sp").value.get());
        assertEquals(points1.getAttribute("ip"), changes.get("ip").value.get());
        assertEquals(points1.getAttribute("pp"), changes.get("pp").value.get());

        report = repo.command(BlameOp.class).setPath(path).setCommit(firstCommit.getId()).call();
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
        RevCommit firstCommit = repo.command(CommitOp.class).call();
        deleteAndAdd(points1);
        RevCommit secondCommit = repo.command(CommitOp.class).call();
        insertAndAdd(points1);
        RevCommit thirdCommit = repo.command(CommitOp.class).call();
        String path = NodeRef.appendChild(pointsName, idP1);
        BlameReport report = repo.command(BlameOp.class).setPath(path).call();
        Map<String, ValueAndCommit> changes = report.getChanges();
        assertEquals(3, changes.size());
        Collection<ValueAndCommit> commits = changes.values();
        for (ValueAndCommit valueAndCommit : commits) {
            assertEquals(thirdCommit, valueAndCommit.commit);
        }

        try {
            report = repo.command(BlameOp.class).setPath(path).setCommit(secondCommit.getId())
                    .call();
            fail();
        } catch (BlameException e) {
            assertTrue(e.statusCode == StatusCode.FEATURE_NOT_FOUND);
        }

        report = repo.command(BlameOp.class).setPath(path).setCommit(firstCommit.getId()).call();
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
        repo.command(CommitOp.class).call();
        try {
            repo.command(BlameOp.class).setPath("wrongpath").call();
            fail();
        } catch (BlameException e) {
            assertTrue(e.statusCode == StatusCode.FEATURE_NOT_FOUND);
        }

    }

    @Test
    public void testBlameWithFeatureType() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).call();
        try {
            repo.command(BlameOp.class).setPath(pointsName).call();
            fail();
        } catch (BlameException e) {
            assertTrue(e.statusCode == StatusCode.PATH_NOT_FEATURE);
        }

    }

}