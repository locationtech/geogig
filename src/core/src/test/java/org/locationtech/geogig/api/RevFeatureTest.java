/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.locationtech.geogig.api.RevObject.TYPE;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class RevFeatureTest {

    @Test
    public void testRevFeatureConstructorAndAccessors() {
        ImmutableList<Optional<Object>> values = ImmutableList.of(
                Optional.of((Object) "StringProp1_1"), Optional.of((Object) new Integer(1000)),
                Optional.of((Object) "POINT(1 1)"));

        RevFeature feature = RevFeatureImpl.build(values);

        assertEquals(TYPE.FEATURE, feature.getType());

        assertEquals(values, feature.getValues());
    }

    @Test
    public void testRevFeatureToString() {
        ImmutableList<Optional<Object>> values = ImmutableList.of(
                Optional.of((Object) "StringProp1_1"), Optional.of((Object) new Integer(1000)),
                Optional.of((Object) "POINT(1 1)"));

        RevFeature feature = RevFeatureImpl.build(values);

        String featureString = feature.toString();

        assertEquals("Feature[" + feature.getId().toString() + "; StringProp, 1000, POINT(1 1)]",
                featureString);
    }
}
