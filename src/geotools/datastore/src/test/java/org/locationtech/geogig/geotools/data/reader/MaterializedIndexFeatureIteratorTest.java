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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.locationtech.geogig.crs.CoordinateReferenceSystem;
import org.locationtech.geogig.feature.Feature;
import org.locationtech.geogig.feature.FeatureType;
import org.locationtech.geogig.feature.FeatureTypes;
import org.locationtech.geogig.feature.PropertyDescriptor;
import org.locationtech.geogig.geotools.adapt.GT;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.test.integration.RepositoryTestCase;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.geometry.BoundingBox;

import com.google.common.collect.Lists;

public class MaterializedIndexFeatureIteratorTest extends RepositoryTestCase {

    private GeometryFactory geometryFactory = new GeometryFactory();

    protected @Override void setUpInternal() throws Exception {
        //
    }

    @Test
    public void testAdaptEmptySchema() throws Exception {
        CoordinateReferenceSystem crs = pointsType.getCoordinateReferenceSystem();

        FeatureType emptySchema = FeatureTypes.createSubType(pointsType, new String[0]);

        AutoCloseableIterator<NodeRef> nodes = AutoCloseableIterator.emptyIterator();
        MaterializedIndexFeatureIterator iterator;
        iterator = MaterializedIndexFeatureIterator.create(emptySchema, nodes, geometryFactory,
                crs);

        NodeRef nodeRef = nodeRef(points1);

        SimpleFeature feature = iterator.adapt(nodeRef);
        assertNotNull(feature);
        assertEquals(emptySchema, GT.adapt(feature.getFeatureType()));
        Envelope expectedBounds = nodeRef.bounds().get();
        BoundingBox fbounds = feature.getBounds();
        assertEquals(expectedBounds, fbounds);
        assertEquals(GT.adapt(crs), fbounds.getCoordinateReferenceSystem());
    }

    @Test
    public void testAdaptFullySupportedSchema() throws Exception {
        CoordinateReferenceSystem crs = pointsType.getCoordinateReferenceSystem();

        FeatureType subType = FeatureTypes.createSubType(pointsType, "pp", "ip");

        AutoCloseableIterator<NodeRef> nodes = AutoCloseableIterator.emptyIterator();
        MaterializedIndexFeatureIterator iterator;
        iterator = MaterializedIndexFeatureIterator.create(subType, nodes, geometryFactory, crs);

        NodeRef nodeRef = nodeRef(points1, "pp", "ip");

        SimpleFeature feature = iterator.adapt(nodeRef);
        assertNotNull(feature);
        assertEquals(subType, GT.adapt(feature.getFeatureType()));

        Envelope expectedBounds = nodeRef.bounds().get();
        BoundingBox fbounds = feature.getBounds();
        assertEquals(expectedBounds, fbounds);
        assertEquals(crs, GT.adapt(fbounds.getCoordinateReferenceSystem()));
    }

    @Test
    public void testIterate() throws Exception {
        CoordinateReferenceSystem crs = pointsType.getCoordinateReferenceSystem();

        FeatureType subType = FeatureTypes.createSubType(pointsType, "pp", "ip");

        NodeRef n1 = nodeRef(points1, "pp", "ip");
        NodeRef n2 = nodeRef(points2, "pp", "ip");
        NodeRef n3 = nodeRef(points3, "pp", "ip");

        AutoCloseableIterator<NodeRef> nodes = AutoCloseableIterator
                .fromIterator(Lists.newArrayList(n1, n2, n3).iterator());

        MaterializedIndexFeatureIterator iterator;
        iterator = MaterializedIndexFeatureIterator.create(subType, nodes, geometryFactory, crs);

        ArrayList<SimpleFeature> features = Lists.newArrayList(iterator);
        assertEquals(3, features.size());
        assertFeature(subType, points1, features.get(0));
        assertFeature(subType, points2, features.get(1));
        assertFeature(subType, points3, features.get(2));
    }

    private void assertFeature(FeatureType expectedType, Feature expectedValues,
            SimpleFeature actual) {
        assertEquals(expectedType, GT.adapt(actual.getFeatureType()));
        assertEquals(expectedValues.getId(), actual.getID());
        assertEquals(expectedValues.getDefaultGeometryBounds(), GT.adapt(actual.getBounds()));

        for (PropertyDescriptor att : expectedType.getDescriptors()) {
            String name = att.getLocalName();
            assertEquals(expectedValues.getAttribute(name), actual.getAttribute(name));
        }
    }

    private NodeRef nodeRef(Feature f, String... extraAttributes) {

        Map<String, Object> extraData = new HashMap<>();
        Map<String, Object> extraAtts = new HashMap<>();
        if (extraAttributes != null) {
            for (String name : extraAttributes) {
                extraAtts.put(name, f.getAttribute(name));
            }
        }
        extraData.put(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA, extraAtts);

        ObjectId id = RevFeature.builder().build(f).getId();
        Envelope bounds = (Envelope) f.getDefaultGeometryBounds();
        Node node = RevObjectFactory.defaultInstance().createNode(f.getId(), id, ObjectId.NULL,
                TYPE.FEATURE, bounds, extraData);

        String typeName = f.getType().getName().getLocalPart();
        NodeRef nodeRef = NodeRef.create(typeName, node);
        return nodeRef;
    }

}
