/*
 *  Copyright (c) 2017 Boundless and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Distribution License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/edl-v10.html
 *
 *  Contributors:
 *  Morgan Thompson (Boundless) - initial implementation
 *  Alex Goudine (Boundless)
 */

package org.locationtech.geogig.data;

import com.google.common.base.Optional;
import com.vividsolutions.jts.geom.Envelope;
import org.junit.Test;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.plumbing.ResolveFeatureType;
import org.locationtech.geogig.porcelain.CommitOp;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.referencing.NoSuchAuthorityCodeException;

public class EPSGBoundsCalcTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
        injector.configDatabase().put("user.name", "mthompson");
        injector.configDatabase().put("user.email", "mthompson@boundlessgeo.com");
    }

    @Test
    public void featureTypeTest() throws Exception {
        insertAndAdd(points1);
        geogig.command(CommitOp.class).setMessage("Commit1").call();

        Optional<RevFeatureType> featureType = geogig.command(ResolveFeatureType.class)
            .setRefSpec("WORK_HEAD:" + NodeRef.appendChild(pointsName, idP1)).call();

        Envelope bounds = new EPSGBoundsCalc().getCRSBounds(featureType);

        Envelope wgs84 = new Envelope(-180.0, 180.0, -90.0, 90.0);
        assertEquals(bounds, wgs84);
    }

    @Test
    public void epsgTest() throws Exception {
        Envelope bounds = null;
        String[] testArray = {"EPSG:4326","EPSG:26910","EPSG:3857","EPSG:3412","EPSG:3411", "EPSG:3031"};
        Envelope[] testEnvelopes = new Envelope[6];
        testEnvelopes[0] = new Envelope(-180.0, 180.0, -90.0, 90.0);
        testEnvelopes[1] = new Envelope(212172.22206537757, 788787.632995196, 3378624.2031936757, 9083749.435906317);
        testEnvelopes[2] = new Envelope(-2.0037508342789244E7, 2.0037508342789244E7, -2.00489661040146E7, 2.0048966104014594E7);
        testEnvelopes[3] = new Envelope(-3323231.542684214, 3323231.542684214, -3323231.542684214, 3323231.542684214);
        testEnvelopes[4] = new Envelope(-2349879.5592850395, 2349879.5592850395, -2349879.5592850395, 2349879.5592850395);
        testEnvelopes[5] = new Envelope(-3333134.027630277, 3333134.027630277, -3333134.027630277, 3333134.027630277);

        for (int i = 0; i < testArray.length; i++) {
            bounds = new EPSGBoundsCalc().findCode(testArray[i]);
            assertEquals(bounds, testEnvelopes[i]);
        }
    }

    @Test(expected=NoSuchAuthorityCodeException.class)
    public void googleProjectionTest() throws Exception{
        Envelope bounds = new EPSGBoundsCalc().findCode("EPSG:900913");
        assertNull(bounds);
    }

    @Test(expected=NoSuchAuthorityCodeException.class)
    public void badCodeTest() throws Exception{
        Envelope bounds = new EPSGBoundsCalc().findCode("random stuff!!!");
        assertNull(bounds);
    }
}
