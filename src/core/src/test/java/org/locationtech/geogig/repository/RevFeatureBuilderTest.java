/* Copyright (c) 2012-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.repository;

import org.junit.Test;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureBuilder;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.opengis.feature.Property;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class RevFeatureBuilderTest extends RepositoryTestCase {

    @Override
    protected void setUpInternal() throws Exception {

    }

    @Test
    public void testBuildEmpty() throws Exception {
        try {
            RevFeatureBuilder.build(null);
            fail("expected IllegalStateException on null feature");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No feature set"));
        }
    }

    @Test
    public void testBuildFull() throws Exception {
        RevFeature feature = RevFeatureBuilder.build(points1);

        ImmutableList<Optional<Object>> values = feature.getValues();

        assertEquals(values.size(), points1.getProperties().size());

        for (Property prop : points1.getProperties()) {
            assertTrue(values.contains(Optional.fromNullable(prop.getValue())));
        }

        RevFeature feature2 = RevFeatureBuilder.build(lines1);

        values = feature2.getValues();

        assertEquals(values.size(), lines1.getProperties().size());

        for (Property prop : lines1.getProperties()) {
            assertTrue(values.contains(Optional.fromNullable(prop.getValue())));
        }

    }
}
