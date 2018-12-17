/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.scripting;

import org.junit.Test;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Feature;

public class GeogigAPITest extends RepositoryTestCase {

    private GeoGigAPI geogigAPI;

    @Override
    protected void setUpInternal() throws Exception {
        geogigAPI = new GeoGigAPI(this.repo);
    }

    @Test
    public void testGetFeaturesToCommit() throws Exception {
        insertAndAdd(points1, points2);
        Feature[] features = geogigAPI.getFeaturesToCommit(null, false);
        assertEquals(2, features.length);
    }

    @Test
    public void testGetFeatureFromHead() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("Commit message").call();
        Feature feature = geogigAPI.getFeatureFromHead(NodeRef.appendChild(pointsName, idP1));
        assertNotNull(feature);
    }

    @Test
    public void testGetFeatureFromWorkingTree() throws Exception {
        insert(points1);
        Feature feature = geogigAPI
                .getFeatureFromWorkingTree(NodeRef.appendChild(pointsName, idP1));
        assertNotNull(feature);
    }

}
