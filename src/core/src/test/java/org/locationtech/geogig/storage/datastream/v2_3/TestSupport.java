/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.datastream.v2_3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.model.Bucket;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.geogig.plumbing.HashObject;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class TestSupport {

    public static List<Node> featureNodes(int count) {
        return nodes(TYPE.FEATURE, count, false, false, false);
    }

    public static List<Node> treeNodes(int count) {
        return nodes(TYPE.TREE, count, true, true, true);
    }

    public static List<Node> nodes(final TYPE type, final int count, boolean withMetadataId,
            boolean withBounds, boolean withExtraData) {

        List<ObjectId> mdids = null;
        if (withMetadataId && count > 0) {
            mdids = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                mdids.add(RevObjectTestSupport.hashString("fakemetadata" + i));
            }
        }
        return nodes(type, count, mdids, withBounds, withExtraData);
    }

    public static List<Node> nodes(final TYPE type, final int count,
            @Nullable List<ObjectId> metadataIds, boolean withBounds, boolean withExtraData) {

        // infinite iterator to repeat values if list size is less than count
        if (null == metadataIds) {
            metadataIds = ImmutableList.of(ObjectId.NULL);
        }
        Iterator<ObjectId> mdids = Iterables.cycle(metadataIds).iterator();

        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name;
            ObjectId oid;
            ObjectId metadataId;
            Envelope bounds;
            Map<String, Object> extraData;

            name = "more-or-less-large-node-name-" + i;
            oid = RevObjectTestSupport.hashString(name);
            metadataId = mdids.next();
            boolean makePoint = i % 2 == 0;
            bounds = withBounds
                    ? (makePoint ? new Envelope(i, i, i, i) : new Envelope(i, i + 1, i, i + 1))
                    : null;

            extraData = withExtraData ? extraData(i) : null;

            Node node = RevObjectFactory.defaultInstance().createNode(name, oid, metadataId, type,
                    bounds, extraData);
            nodes.add(node);
        }
        return nodes;
    }

    public static Map<String, Object> extraData(int i) {

        Map<String, Object> map1, map2, extraData;
        map1 = new HashMap<>();
        map2 = new TreeMap<>();

        map1.put("long", Long.valueOf(123));
        map2.put("long", Long.valueOf(123));

        map1.put("int", Integer.valueOf(456));
        map2.put("int", Integer.valueOf(456));

        map1.put("string", "hello");
        map2.put("string", "hello");

        map1.put("geom", geom("LINESTRING(1 1, 1.1 2.1, 100 1000)"));
        map2.put("geom", geom("LINESTRING(1 1, 1.1 2.1, 100 1000)"));

        extraData = ImmutableMap.of("I", (Object) "am", "a", (Object) "different", "map than",
                (Object) map1, "and", (Object) map2);

        Map<String, Object> map = new HashMap<>();
        map.put("prop1", Integer.valueOf(i));
        map.put("prop2", "String prop value " + i);
        map.put("uuid", UUID.randomUUID());
        map.put("duplicatedValue", "same value for all");
        map.put("mapProp", extraData);
        try {
            map.put("geom", new WKTReader().read(
                    String.format("LINESTRING(%d %d, 0 0, 1 1, 2 2, 3 3, 4 4, 5 5, 6 6)", i, i)));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        return map;
    }

    private static Geometry geom(String wkt) {
        try {
            return new WKTReader().read(wkt);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static RevTree tree(int treeSize, List<Node> treeNodes, List<Node> featureNodes,
            SortedSet<Bucket> buckets) {

        ObjectId id = HashObject.hashTree(treeNodes, featureNodes, buckets);

        ImmutableList<Node> trees = treeNodes == null ? ImmutableList.of()
                : ImmutableList.copyOf(treeNodes);
        ImmutableList<Node> features = featureNodes == null ? ImmutableList.of()
                : ImmutableList.copyOf(featureNodes);
        buckets = buckets == null ? Collections.emptySortedSet() : buckets;
        int childTreeCount = treeNodes == null ? 0 : treeNodes.size();
        if (buckets.isEmpty()) {
            return RevObjectFactory.defaultInstance().createTree(id, treeSize, trees, features);
        }
        return RevObjectFactory.defaultInstance().createTree(id, treeSize, childTreeCount, buckets);
    }

    public static void assertEqualsFully(RevTree o1, RevTree o2) {
        assertEquals(o1.size(), o2.size());
        assertEquals(o1.numTrees(), o2.numTrees());
        assertEqualsFully(o1.features(), o2.features());
        assertEqualsFully(o1.trees(), o2.trees());
        assertEquals(Lists.newArrayList(o1.getBuckets()), Lists.newArrayList(o2.getBuckets()));
    }

    public static void assertEqualsFully(List<Node> expected, List<Node> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Node orig = expected.get(i);
            Node lazy = actual.get(i);
            assertEquals(orig.getName(), lazy.getName());
            assertEquals(orig.getObjectId(), lazy.getObjectId());
            assertEquals(orig.getType(), lazy.getType());
            assertEquals(orig.getMetadataId(), lazy.getMetadataId());
            assertEnvelope("at index " + i, orig.bounds().orNull(), lazy.bounds().orNull());
            Map<String, Object> expectedExtraData = orig.getExtraData();
            Map<String, Object> actualExtraData = lazy.getExtraData();
            assertEquals(expectedExtraData, actualExtraData);

            assertEquals(orig, lazy);
        }
    }

    public static void assertEnvelope(String message, Envelope expected, Envelope actual) {
        if (expected == null) {
            assertNull(message, actual);
        } else {
            assertEquals(message, expected.getMinX(), actual.getMinX(), 1E-7);
            assertEquals(message, expected.getMinY(), actual.getMinY(), 1E-7);
            assertEquals(message, expected.getMaxX(), actual.getMaxX(), 1E-7);
            assertEquals(message, expected.getMaxY(), actual.getMaxY(), 1E-7);
        }
    }

}
