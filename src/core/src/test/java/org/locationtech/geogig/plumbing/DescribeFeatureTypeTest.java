/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.test.integration.RepositoryTestCase;

public class DescribeFeatureTypeTest extends RepositoryTestCase {

    RevFeatureType featureType;

    protected @Override void setUpInternal() throws Exception {
        featureType = RevFeatureType.builder().type(pointsType).build();
    }

    @Test
    public void testDescribeNullFeatureType() throws Exception {
        DescribeFeatureType describe = new DescribeFeatureType();

        try {
            describe.call();
            fail("expected IllegalStateException on null feature type");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("FeatureType has not been set"));
        }
    }

    @Test
    public void testDescribeFeatureType() throws Exception {
        DescribeFeatureType describe = new DescribeFeatureType();

        List<PropertyDescriptor> properties = describe.setFeatureType(featureType).call();

        for (PropertyDescriptor prop : properties) {
            assertTrue(pointsType.getDescriptors().contains(prop));
        }

    }
}
