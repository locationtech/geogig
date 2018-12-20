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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.identity.FeatureId;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import com.google.common.annotations.VisibleForTesting;

/**
 * A {@link SimpleFeature} iterator used to build features out of nodes {@link Node#getExtraData()
 * extra data} maps.
 * <p>
 * When the nodes in the provided {@link NodeRef} iterator are known to contain the required
 * attribute values on their extra data map, this iterator is used to avoid querying the repository
 * for the actual {@link RevFeature} objects and instead built {@link SimpleFeature}s directly from
 * the data stored with the nodes.
 */
class MaterializedIndexFeatureIterator implements AutoCloseableIterator<SimpleFeature> {

    private final AutoCloseableIterator<NodeRef> nodes;

    private final SimpleFeatureBuilder featureBuilder;

    private final GeometryFactory geometryFactory;

    private final CoordinateReferenceSystem crs;

    private MaterializedIndexFeatureIterator(final SimpleFeatureBuilder builder,
            AutoCloseableIterator<NodeRef> nodes, GeometryFactory geometryFactory,
            CoordinateReferenceSystem crs) {
        this.featureBuilder = builder;
        this.nodes = nodes;
        this.geometryFactory = geometryFactory;
        this.crs = crs;
    }

    public static MaterializedIndexFeatureIterator create(SimpleFeatureType outputSchema,
            AutoCloseableIterator<NodeRef> nodes, GeometryFactory geometryFactory,
            CoordinateReferenceSystem crs) {

        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(outputSchema);
        return new MaterializedIndexFeatureIterator(builder, nodes, geometryFactory, crs);
    }

    @Override
    public void close() {
        nodes.close();
    }

    @Override
    public boolean hasNext() {
        try {
            return nodes.hasNext();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    public SimpleFeature next() {
        try {
            if (!nodes.hasNext()) {
                throw new NoSuchElementException();
            }
            NodeRef node = nodes.next();
            SimpleFeature feature = adapt(node);
            return feature;
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    /**
     * Creates a {@link SimpleFeature} out of a Node's {@link Node#getExtraData() extra data} map
     * <p>
     * The {@link #featureBuilder} shall be configured with a {@link FeatureType} whose attributes
     * are present in the node's extra data.
     */
    @VisibleForTesting
    SimpleFeature adapt(NodeRef node) {

        final SimpleFeatureType featureType = featureBuilder.getFeatureType();
        final List<AttributeDescriptor> attributeDescriptors = featureType
                .getAttributeDescriptors();
        if (attributeDescriptors.isEmpty()) {

            return BoundedSimpleFeature.empty(featureType, node.getNode(), crs);

        } else {
            final Map<String, Object> materializedAttributes;
            materializedAttributes = IndexInfo.getMaterializedAttributes(node.getNode());
            checkNotNull(materializedAttributes);

            featureBuilder.reset();
            for (int i = 0; i < attributeDescriptors.size(); i++) {
                AttributeDescriptor descriptor = attributeDescriptors.get(i);
                String localName = descriptor.getLocalName();
                Object value = materializedAttributes.get(localName);
                if (value instanceof Geometry) {
                    value = geometryFactory.createGeometry((Geometry) value);
                }
                featureBuilder.set(localName, value);
            }
        }
        String id = node.name();
        SimpleFeature feature = featureBuilder.buildFeature(id);
        return feature;
    }

    /**
     * Provides SimpleFeature implementations that can still return their bounding box even if they
     * don't have a geometry attribute set, getting it from the underlying feature
     * {@link Node#bounds() node bounds}
     */
    private static class BoundedSimpleFeature extends SimpleFeatureImpl {

        private ReferencedEnvelope bounds;

        BoundedSimpleFeature(List<Object> v, SimpleFeatureType t, FeatureId fid,
                ReferencedEnvelope bounds) {
            super(v, t, fid);
            this.bounds = bounds;
        }

        @Override
        public BoundingBox getBounds() {
            return bounds;
        }

        static BoundedSimpleFeature empty(SimpleFeatureType type, Node node,
                CoordinateReferenceSystem crs) {

            FeatureId fid = new FeatureIdImpl(node.getName());
            ReferencedEnvelope env = new ReferencedEnvelope(crs);
            node.expand(env);
            return new BoundedSimpleFeature(Collections.emptyList(), type, fid, env);
        }
    }
}
