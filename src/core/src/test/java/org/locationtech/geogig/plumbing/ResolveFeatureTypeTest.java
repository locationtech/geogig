/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

public class ResolveFeatureTypeTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected @Override void setUpInternal() throws Exception {
        repo.context().configDatabase().put("user.name", "groldan");
        repo.context().configDatabase().put("user.email", "groldan@test.com");
    }

    @Test
    public void testResolveFeatureType() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).setMessage("Commit1").call();

        Optional<RevFeatureType> featureType = repo.command(ResolveFeatureType.class)
                .setRefSpec(pointsName).call();
        assertTrue(featureType.isPresent());
        assertEquals(pointsName, featureType.get().getName().getLocalPart());
        assertEquals(TYPE.FEATURETYPE, featureType.get().getType());
    }

    @Test
    public void testResolveFeatureTypeWithColonInFeatureTypeName() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).setMessage("Commit1").call();

        Optional<RevFeatureType> featureType = repo.command(ResolveFeatureType.class)
                .setRefSpec("WORK_HEAD:" + pointsName).call();
        assertTrue(featureType.isPresent());
        assertEquals(pointsName, featureType.get().getName().getLocalPart());
        assertEquals(TYPE.FEATURETYPE, featureType.get().getType());
    }

    @Test
    public void testNoFeatureTypeNameSpecified() {
        exception.expect(IllegalStateException.class);
        repo.command(ResolveFeatureType.class).call();
    }

    @Test
    public void testObjectNotInIndex() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).setMessage("Commit1").call();

        Optional<RevFeatureType> featureType = repo.command(ResolveFeatureType.class)
                .setRefSpec("WORK_HEAD:" + linesName).call();
        assertFalse(featureType.isPresent());
    }

    @Test
    public void testResolveFeatureTypeFromFeatureRefspec() throws Exception {
        insertAndAdd(points1);
        repo.command(CommitOp.class).setMessage("Commit1").call();

        Optional<RevFeatureType> featureType = repo.command(ResolveFeatureType.class)
                .setRefSpec("WORK_HEAD:" + NodeRef.appendChild(pointsName, idP1)).call();
        assertTrue(featureType.isPresent());
    }
}
