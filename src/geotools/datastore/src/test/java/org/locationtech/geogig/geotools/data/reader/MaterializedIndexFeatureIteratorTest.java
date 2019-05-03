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

import org.geotools.data.DataUtilities;
import org.junit.Test;
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
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.collect.Lists;

public class MaterializedIndexFeatureIteratorTest extends RepositoryTestCase {

    private GeometryFactory geometryFactory = new GeometryFactory();

    @Override
    protected void setUpInternal() throws Exception {
        //
    }

    @Test
    public void testAdaptEmptySchema() throws Exception {
        CoordinateReferenceSystem crs = pointsType.getCoordinateReferenceSystem();

        SimpleFeatureType emptySchema = DataUtilities.createSubType(pointsType, new String[0]);

        AutoCloseableIterator<NodeRef> nodes = AutoCloseableIterator.emptyIterator();
        MaterializedIndexFeatureIterator iterator;
        iterator = MaterializedIndexFeatureIterator.create(emptySchema, nodes, geometryFactory,
                crs);

        NodeRef nodeRef = nodeRef((SimpleFeature) points1);

        SimpleFeature feature = iterator.adapt(nodeRef);
        assertNotNull(feature);
        assertEquals(emptySchema, feature.getFeatureType());
        Envelope expectedBounds = nodeRef.bounds().get();
        BoundingBox fbounds = feature.getBounds();
        assertEquals(expectedBounds, fbounds);
        assertEquals(crs, fbounds.getCoordinateReferenceSystem());
    }

    @Test
    public void testAdaptFullySupportedSchema() throws Exception {
        CoordinateReferenceSystem crs = pointsType.getCoordinateReferenceSystem();

        SimpleFeatureType subType = DataUtilities.createSubType(pointsType,
                new String[] { "pp", "ip" });

        AutoCloseableIterator<NodeRef> nodes = AutoCloseableIterator.emptyIterator();
        MaterializedIndexFeatureIterator iterator;
        iterator = MaterializedIndexFeatureIterator.create(subType, nodes, geometryFactory, crs);

        NodeRef nodeRef = nodeRef((SimpleFeature) points1, "pp", "ip");

        SimpleFeature feature = iterator.adapt(nodeRef);
        assertNotNull(feature);
        assertEquals(subType, feature.getFeatureType());

        Envelope expectedBounds = nodeRef.bounds().get();
        BoundingBox fbounds = feature.getBounds();
        assertEquals(expectedBounds, fbounds);
        assertEquals(crs, fbounds.getCoordinateReferenceSystem());
    }

    @Test
    public void testIterate() throws Exception {
        CoordinateReferenceSystem crs = pointsType.getCoordinateReferenceSystem();

        SimpleFeatureType subType = DataUtilities.createSubType(pointsType,
                new String[] { "pp", "ip" });

        NodeRef n1 = nodeRef((SimpleFeature) points1, "pp", "ip");
        NodeRef n2 = nodeRef((SimpleFeature) points2, "pp", "ip");
        NodeRef n3 = nodeRef((SimpleFeature) points3, "pp", "ip");

        AutoCloseableIterator<NodeRef> nodes = AutoCloseableIterator
                .fromIterator(Lists.newArrayList(n1, n2, n3).iterator());

        MaterializedIndexFeatureIterator iterator;
        iterator = MaterializedIndexFeatureIterator.create(subType, nodes, geometryFactory, crs);

        ArrayList<SimpleFeature> features = Lists.newArrayList(iterator);
        assertEquals(3, features.size());
        assertFeature(subType, (SimpleFeature) points1, features.get(0));
        assertFeature(subType, (SimpleFeature) points2, features.get(1));
        assertFeature(subType, (SimpleFeature) points3, features.get(2));
    }

    private void assertFeature(SimpleFeatureType expectedType, SimpleFeature expectedValues,
            SimpleFeature actual) {
        assertEquals(expectedType, actual.getFeatureType());
        assertEquals(expectedValues.getID(), actual.getID());
        assertEquals(expectedValues.getBounds(), actual.getBounds());

        for (AttributeDescriptor att : expectedType.getAttributeDescriptors()) {
            String name = att.getLocalName();
            assertEquals(expectedValues.getAttribute(name), actual.getAttribute(name));
        }
    }

    private NodeRef nodeRef(SimpleFeature f, String... extraAttributes) {

        Map<String, Object> extraData = new HashMap<>();
        Map<String, Object> extraAtts = new HashMap<>();
        if (extraAttributes != null) {
            for (String name : extraAttributes) {
                extraAtts.put(name, f.getAttribute(name));
            }
        }
        extraData.put(IndexInfo.FEATURE_ATTRIBUTES_EXTRA_DATA, extraAtts);

        ObjectId id = RevFeature.builder().build(f).getId();
        Envelope bounds = (Envelope) f.getBounds();
        Node node = RevObjectFactory.defaultInstance().createNode(f.getID(), id, ObjectId.NULL,
                TYPE.FEATURE, bounds, extraData);

        String typeName = f.getType().getTypeName();
        NodeRef nodeRef = NodeRef.create(typeName, node);
        return nodeRef;
    }

}
