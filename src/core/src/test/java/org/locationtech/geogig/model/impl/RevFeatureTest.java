/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.model.impl;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjects;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

public class RevFeatureTest {

    @Test
    public void testRevFeatureConstructorAndAccessors() {
        List<Object> values = Lists.newArrayList("StringProp1_1", new Integer(1000), "POINT(1 1)");

        RevFeature feature = RevFeature.builder().addAll(values).build();

        assertEquals(TYPE.FEATURE, feature.getType());

        assertEquals(Lists.transform(values, (v) -> Optional.fromNullable(v)), feature.getValues());
    }

    @Test
    public void testRevFeatureToString() {
        List<Object> values = Lists.newArrayList("StringProp1_1", new Integer(1000), "POINT(1 1)");

        RevFeature feature = RevFeature.builder().addAll(values).build();

        String featureString = feature.toString();
        String expected = RevObjects.toString(feature);
        assertEquals(expected, featureString);
    }
}
