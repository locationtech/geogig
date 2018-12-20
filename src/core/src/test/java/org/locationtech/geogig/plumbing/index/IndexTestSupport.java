/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.plumbing.index;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.geotools.data.DataUtilities;
import org.geotools.feature.SchemaException;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.model.RevFeatureBuilder;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.CanonicalTreeBuilder;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.LsTreeOp;
import org.locationtech.geogig.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.IndexInfo;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.WorkingTree;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.repository.impl.SpatialOps;
import org.locationtech.geogig.storage.ObjectStore;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.collect.Lists;

public class IndexTestSupport {

    public static void verifyIndex(GeoGIG geogig, ObjectId indexTreeId, ObjectId canonicalTreeId,
            String... extraAttributes) {
        verifyIndex(geogig.getContext(), indexTreeId, canonicalTreeId, extraAttributes);
    }

    public static void verifyIndex(Context context, ObjectId indexTreeId, ObjectId canonicalTreeId,
            String... extraAttributes) {
        Iterator<NodeRef> canonicalFeatures = context.command(LsTreeOp.class)
                .setReference(canonicalTreeId.toString())
                .setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES).call();

        Iterator<NodeRef> indexFeatures = context.command(LsTreeOp.class)
                .setReference(indexTreeId.toString()).setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES)
                .setSource(context.indexDatabase()).call();

        List<NodeRef> canonicalFeaturesList = Lists.newArrayList(canonicalFeatures);
        while (indexFeatures.hasNext()) {
            NodeRef indexFeature = indexFeatures.next();
            if (extraAttributes.length > 0) {
                Map<String, Object> featureExtraData = indexFeature.getNode().getExtraData();
                assertNotNull("Node has no extra data: " + indexFeature.getNode(),
                        featureExtraData);
                Map<String, Object> featureExtraAttributes = IndexInfo
                        .getMaterializedAttributes(indexFeature.getNode());
                assertEquals(extraAttributes.length, featureExtraAttributes.size());
                for (String attribute : extraAttributes) {
                    assertTrue(featureExtraAttributes.containsKey(attribute));
                    featureExtraAttributes.remove(attribute);
                }
                assertTrue(featureExtraAttributes.isEmpty());
            }
            assertTrue(canonicalFeaturesList.contains(indexFeature));
            canonicalFeaturesList.remove(indexFeature);
        }
        assertTrue(canonicalFeaturesList.isEmpty());
    }

    public static GeometryFactory gf = new GeometryFactory();

    public static RevFeature createPointFeature(double x, double y, Object... extraAttribues) {
        RevFeatureBuilder builder = RevFeature.builder();
        builder.addValue(gf.createPoint(new Coordinate(x, y)));
        if (extraAttribues != null) {
            builder.addAll(Lists.newArrayList(extraAttribues));
        }
        RevFeature feature = builder.build();
        return feature;
    }

    public static List<RevFeature> createWorldPointFeatures() {
        List<RevFeature> features = new ArrayList<>();
        for (int x = -180; x <= 180; x++) {
            for (int y = -90; y <= 90; y++) {
                RevFeature feature = createPointFeature(x, y);
                features.add(feature);
            }
        }
        return features;
    }

    public static String getPointFid(double x, double y) {
        return String.format("%03f,%03f", x, y);
    }

    public static RevTree createWorldPointsTree(Repository repository) {

        ObjectStore store = repository.objectDatabase();
        CanonicalTreeBuilder builder = CanonicalTreeBuilder.create(store);
        for (int x = -180; x <= 180; x += 5) {
            for (int y = -90; y <= 90; y += 5) {

                String fid = getPointFid(x, y);

                RevFeature feature;
                feature = createPointFeature(x, y, Double.valueOf(x), Double.valueOf(y), fid);
                Envelope env = SpatialOps.boundsOf(feature);

                ObjectId oid = feature.getId();
                Node node = RevObjectFactory.defaultInstance().createNode(fid, oid, ObjectId.NULL,
                        TYPE.FEATURE, env, null);
                store.put(feature);
                builder.put(node);
            }
        }
        RevTree tree = builder.build();
        return tree;
    }

    public static List<Node> createWorldPointsNodes(int degreeStep) {
        List<Node> nodes = new ArrayList<>();
        for (int x = -180; x <= 180; x += degreeStep) {
            for (int y = -90; y <= 90; y += degreeStep) {

                String fid = getPointFid(x, y);
                Envelope env = new Envelope(new Coordinate(x, y));
                ObjectId oid = RevObjectTestSupport.hashString(fid);
                Node node = RevObjectFactory.defaultInstance().createNode(fid, oid, ObjectId.NULL,
                        TYPE.FEATURE, env, null);
                nodes.add(node);
            }
        }
        return nodes;
    }

    public static SimpleFeatureType featureType = null;

    public static NodeRef createWorldPointsLayer(Repository repository) {
        if (featureType == null) {
            String typeSpec = "geom:Point:srid=4326,x:Double,y:Double,xystr:String";
            try {
                featureType = DataUtilities.createType("worldpoints", typeSpec);
            } catch (SchemaException e) {
                throw new RuntimeException(e);
            }
        }
        RevTree tree = createWorldPointsTree(repository);
        WorkingTree workingTree = repository.workingTree();
        NodeRef typeTreeRef = workingTree.createTypeTree(featureType.getTypeName(), featureType);

        ObjectStore store = repository.objectDatabase();
        CanonicalTreeBuilder newRootBuilder = CanonicalTreeBuilder.create(store,
                workingTree.getTree());

        NodeRef newTypeTreeRef = typeTreeRef.update(tree.getId(), SpatialOps.boundsOf(tree));
        newRootBuilder.put(newTypeTreeRef.getNode());
        RevTree newWorkTree = newRootBuilder.build();
        workingTree.updateWorkHead(newWorkTree.getId());
        return newTypeTreeRef;
    }

}
