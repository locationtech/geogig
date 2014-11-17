/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api;

import java.util.ArrayList;

import org.junit.Test;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.type.PropertyDescriptor;

import com.google.common.collect.Lists;

public class RevFeatureTypeTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
        injector.configDatabase().put("user.name", "groldan");
        injector.configDatabase().put("user.email", "groldan@opengeo.org");
    }

    @Test
    public void testConstructorAndAccessors() {
        RevFeatureType featureType = RevFeatureTypeImpl.build(linesType);

        assertEquals(RevObject.TYPE.FEATURETYPE, featureType.getType());

        assertEquals(linesType, featureType.type());

        assertEquals(linesType.getName(), featureType.getName());

        ArrayList<PropertyDescriptor> descriptors = Lists.newArrayList(linesType.getDescriptors());
        // Collections.sort(descriptors, RevFeatureType.PROPERTY_ORDER);
        assertEquals(descriptors, featureType.sortedDescriptors());
    }

    @Test
    public void testToString() {
        RevFeatureType featureType = RevFeatureTypeImpl.build(linesType);

        String featureTypeString = featureType.toString();

        assertEquals("FeatureType[" + featureType.getId().toString() + "; "
                + "sp: String, ip: Integer, pp: LineString]", featureTypeString);
    }
}
