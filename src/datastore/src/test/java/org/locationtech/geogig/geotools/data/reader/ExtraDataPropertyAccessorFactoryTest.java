/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.data.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.locationtech.geogig.model.impl.RevObjectTestSupport.hashString;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.factory.Hints;
import org.geotools.filter.expression.PropertyAccessor;
import org.geotools.filter.expression.PropertyAccessorFactory;
import org.geotools.filter.expression.PropertyAccessors;
import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.geotools.data.reader.ExtraDataPropertyAccessorFactory.ExtraDataPropertyAccesor;
import org.locationtech.geogig.model.Bounded;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.jts.geom.Envelope;

import com.google.common.collect.ImmutableMap;

public class ExtraDataPropertyAccessorFactoryTest {

    private Node testNode;

    private NodeRef testNodeRef;

    private Bucket testBucket;

    @Before
    public void before() {
        ObjectId oid = hashString("id");
        ObjectId metadataId = hashString("metadata");
        Envelope bounds = new Envelope(0, 180, 0, 90);

        Map<String, Object> materializedAttributes = ImmutableMap.of(//
                "int", 1, //
                "double", 0.5, //
                "date", new Date(1486344231314L));

        Map<String, Object> extraData = ImmutableMap.of(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA,
                materializedAttributes);

        testNode = RevObjectFactory.defaultInstance().createNode("test", oid, metadataId,
                TYPE.FEATURE, bounds, extraData);

        testNodeRef = new NodeRef(testNode, "fakeLayerName", metadataId);

        ObjectId bucketId = hashString("bucketId");
        testBucket = RevObjectFactory.defaultInstance().createBucket(bucketId, 0, bounds);
    }

    @Test
    public void testLowestLevelSPI() {
        Iterator<PropertyAccessorFactory> iterator;
        iterator = ServiceLoader.load(PropertyAccessorFactory.class).iterator();

        boolean found = false;
        List<String> available = new ArrayList<>();
        while (iterator.hasNext()) {
            PropertyAccessorFactory factory = iterator.next();
            if (factory instanceof ExtraDataPropertyAccessorFactory) {
                found = true;
            }
            available.add(factory.getClass().getName());
        }

        assertTrue("Available: " + available, found);
    }

    /**
     * Is the factory picked up from
     * {@code META-INF/services/org.geotools.filter.expression.PropertyAccessorFactory}
     */
    @Test
    public void testLowLevelSPI() {
        Iterator<PropertyAccessorFactory> factories;
        factories = ServiceLoader.load(PropertyAccessorFactory.class).iterator();
        while (factories.hasNext()) {
            PropertyAccessorFactory factory = factories.next();
            if (factory instanceof ExtraDataPropertyAccessorFactory) {
                assertTrue(true);
                return;
            }
        }
        fail(ExtraDataPropertyAccessorFactory.class.getName() + " not found");
    }

    @Test
    public void testSPI() {
        findPropertyAccessor(testNode);
        findPropertyAccessor(testNodeRef);
        findPropertyAccessor(testBucket);
    }

    private ExtraDataPropertyAccesor findPropertyAccessor(Object object) {
        String xpath = "int";
        Class target = Integer.class;
        Hints hints = null;

        List<PropertyAccessor> accessors = PropertyAccessors.findPropertyAccessors(object, xpath,
                target, hints);
        for (PropertyAccessor pa : accessors) {
            if (pa instanceof ExtraDataPropertyAccessorFactory.ExtraDataPropertyAccesor) {
                return (ExtraDataPropertyAccesor) pa;
            }
        }

        fail(ExtraDataPropertyAccessorFactory.ExtraDataPropertyAccesor.class.getName()
                + " not found: " + accessors);
        return null;
    }

    @Test
    public void evaluateNode() {
        testEvaluate(testNode, "int", 1);
        testEvaluate(testNode, "int", 1L);
        testEvaluate(testNode, "int", 1D);
        testEvaluate(testNode, "double", 0.5D);
        testEvaluate(testNode, "double", "0.5");
    }

    @Test
    public void evaluateNodeRef() {
        testEvaluate(testNodeRef, "int", 1);
        testEvaluate(testNodeRef, "int", 1L);
        testEvaluate(testNodeRef, "int", 1D);
        testEvaluate(testNodeRef, "double", 0.5D);
        testEvaluate(testNodeRef, "double", "0.5");
    }

    @Test
    public void evaluateId() {
        testEvaluate(testNodeRef, "@id", "test");
        testEvaluate(testNode, "@id", "test");
        testEvaluate(testBucket, "@id", null);
    }

    @Test
    public void evaluateBucket() {
        assertNotNull(findPropertyAccessor(testBucket));
        assertNull(findPropertyAccessor(testBucket).get(testBucket, "int", Integer.class));
    }

    private void testEvaluate(Bounded bounded, String property, @Nullable final Object expected) {
        ExtraDataPropertyAccesor accesor = findPropertyAccessor(bounded);
        Class<? extends Object> target = expected == null ? null : expected.getClass();
        Object actual = accesor.get(bounded, property, target);
        if (expected == null) {
            assertNull(actual);
        } else {
            assertEquals(expected, actual);
        }
    }
}
