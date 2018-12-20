/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.repository;

import static org.junit.Assert.assertEquals;

import java.util.function.Consumer;

import org.junit.Test;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class FeatureInfoTest {

    @Test
    public void testConstructorAndAccessors() {
        final ObjectId oid1 = ObjectId.valueOf("abc123000000000000001234567890abcdef0000");

        RevFeature testFeature = new RevFeature() {
            @Override
            public TYPE getType() {
                return RevObject.TYPE.FEATURE;
            }

            @Override
            public ObjectId getId() {
                return ObjectId.NULL;
            }

            @Override
            public ImmutableList<Optional<Object>> getValues() {
                return ImmutableList.of();
            }

            @Override
            public int size() {
                return 0;
            }

            @Override
            public Optional<Object> get(int index) {
                return Optional.absent();
            }

            @Override
            public void forEach(Consumer<Object> consumer) {
            }

            @Override
            public Optional<Geometry> get(int index, GeometryFactory gf) {
                throw new UnsupportedOperationException();
            }
        };
        FeatureInfo info = FeatureInfo.insert(testFeature, oid1, "Points/1");
        assertEquals(testFeature, info.getFeature());
        assertEquals(oid1, info.getFeatureTypeId());
        assertEquals("Points/1", info.getPath());
    }
}
