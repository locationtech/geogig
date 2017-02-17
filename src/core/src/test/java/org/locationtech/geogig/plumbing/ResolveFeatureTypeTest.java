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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

import com.google.common.base.Optional;

public class ResolveFeatureTypeTest extends RepositoryTestCase {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Override
    protected void setUpInternal() throws Exception {
        injector.configDatabase().put("user.name", "groldan");
        injector.configDatabase().put("user.email", "groldan@boundlessgeo.com");
    }

    @Test
    public void testResolveFeatureType() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("Commit1").call();

        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec(pointsName).call();
        assertTrue(featureType.isPresent());
        assertEquals(pointsName, featureType.get().getName().getLocalPart());
        assertEquals(TYPE.FEATURETYPE, featureType.get().getType());
    }

    @Test
    public void testResolveFeatureTypeWithColonInFeatureTypeName() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("Commit1").call();

        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec("WORK_HEAD:" + pointsName).call();
        assertTrue(featureType.isPresent());
        assertEquals(pointsName, featureType.get().getName().getLocalPart());
        assertEquals(TYPE.FEATURETYPE, featureType.get().getType());
    }

    @Test
    public void testNoFeatureTypeNameSpecified() {
        exception.expect(IllegalStateException.class);
        geogig.command(ResolveFeatureType.class).call();
    }

    @Test
    public void testObjectNotInIndex() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("Commit1").call();

        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec("WORK_HEAD:" + linesName).call();
        assertFalse(featureType.isPresent());
    }

    @Test
    public void testResolveFeatureTypeFromFeatureRefspec() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("Commit1").call();

        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
                .setRefSpec("WORK_HEAD:" + NodeRef.appendChild(pointsName, idP1)).call();
        assertTrue(featureType.isPresent());
    }
}
