/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.data;

import org.junit.Test;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;

public class FeatureBuilderTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {
        injector.configDatabase().put("user.name", "groldan");
        injector.configDatabase().put("user.email", "groldan@boundlessgeo.com");
    }

    @Test
    public void testFeatureBuilder() {
        RevFeatureType revPointsType = RevFeatureType.builder().type(pointsType).build();
        FeatureBuilder builder = new FeatureBuilder(revPointsType);
        RevFeature point1 = RevFeature.builder().build(points1);

        Feature test = builder.build(idP1, point1);

        // assertEquals(points1.getValue(), test.getValue());
        assertEquals(points1.getName(), test.getName());
        assertEquals(points1.getIdentifier(), test.getIdentifier());
        assertEquals(points1.getType(), test.getType());
        assertEquals(points1.getUserData(), test.getUserData());

        RevFeature feature = RevFeature.builder().build(test);
        Feature test2 = builder.build(idP1, feature);

        assertEquals(((SimpleFeature) test).getAttributes(),
                ((SimpleFeature) test2).getAttributes());
    }
}
