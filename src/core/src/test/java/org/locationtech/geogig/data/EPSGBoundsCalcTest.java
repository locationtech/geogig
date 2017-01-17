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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opengis.referencing.NoSuchAuthorityCodeException;

import com.vividsolutions.jts.geom.Envelope;


public class EPSGBoundsCalcTest  {

    @Test
    public void epsgTest() throws Exception {

        String[] testArray = {"EPSG:3031","EPSG:26910","EPSG:3857","EPSG:3412","EPSG:3411"};
        Envelope[] testEnvelopes = new Envelope[5];

        testEnvelopes[0] = new Envelope(-3333134.027630277, 3333134.027630277, -3333134.027630277, 3333134.027630277);
        testEnvelopes[1] = new Envelope(212172.22206537757, 788787.632995196, 3378624.2031936757, 9083749.435906317);
        testEnvelopes[2] = new Envelope(-2.0037508342789244E7, 2.0037508342789244E7, -2.00489661040146E7, 2.0048966104014594E7);
        testEnvelopes[3] = new Envelope(-3323231.542684214, 3323231.542684214, -3323231.542684214, 3323231.542684214);
        testEnvelopes[4] = new Envelope(-2349879.5592850395, 2349879.5592850395, -2349879.5592850395, 2349879.5592850395);

        Envelope bounds;
        for (int i=0; i<testArray.length; i++) {
            bounds = new EPSGBoundsCalc().getCRSBounds(testArray[i]);
            assertEquals(testEnvelopes[i], bounds);
        }
    }

    @Test(expected=NoSuchAuthorityCodeException.class)
    public void googleProjectionTest() throws Exception{
        new EPSGBoundsCalc().getCRSBounds("EPSG:900913");
    }

    @Test(expected=NoSuchAuthorityCodeException.class)
    public void badCodeTest() throws Exception{
        new EPSGBoundsCalc().getCRSBounds("random stuff!!!");
    }
}
