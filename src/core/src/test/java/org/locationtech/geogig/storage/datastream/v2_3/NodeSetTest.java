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

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.locationtech.geogig.model.Node;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevObjectFactory;
import org.locationtech.geogig.model.impl.RevObjectTestSupport;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;

public class NodeSetTest {

    private ByteArrayOutputStream buff;

    private DataOutput out;

    private StringTable stringTable;

    @Before
    public void before() {
        buff = new ByteArrayOutputStream();
        out = ByteStreams.newDataOutput(buff);// this one does not need flush
        stringTable = StringTable.unique();

        // add some initial content to the buffer to avoid zero index matching by coincidence
        buff.write(1);
        buff.write(2);

        // add some garbage to the string table to avoid index matches by coincidence
        stringTable.add("fake string 1");
        stringTable.add("fake string 2");
    }

    @Test
    public void emptyList() throws IOException {
        encodedDecode(featureNodes(0), TYPE.FEATURE);
    }

    @Test
    public void singleFeature() throws IOException {
        encodedDecode(featureNodes(1), TYPE.FEATURE);
    }

    @Test
    public void severalFeatures() throws IOException {
        encodedDecode(nodes(TYPE.FEATURE, 1024, false, false, false), TYPE.FEATURE);
        encodedDecode(nodes(TYPE.FEATURE, 1024, false, false, true), TYPE.FEATURE);
        encodedDecode(nodes(TYPE.FEATURE, 1024, false, true, true), TYPE.FEATURE);
        encodedDecode(nodes(TYPE.FEATURE, 1024, true, true, true), TYPE.FEATURE);
        encodedDecode(nodes(TYPE.FEATURE, 1024, true, false, false), TYPE.FEATURE);
        encodedDecode(nodes(TYPE.FEATURE, 1024, true, true, false), TYPE.FEATURE);
    }

    @Test
    public void severalFeaturesWithExtraData() throws IOException {
        encodedDecode(featureNodesWithExtraData(1024), TYPE.FEATURE);
    }

    @Test
    public void emptyTreeList() throws IOException {
        encodedDecode(treeNodes(0), TYPE.TREE);
    }

    @Test
    public void singleTree() throws IOException {
        encodedDecode(treeNodes(1), TYPE.TREE);
    }

    @Test
    public void singleSeveralTrees() throws IOException {
        encodedDecode(treeNodes(1024), TYPE.TREE);
    }

    @Test
    public void testLazyNodeGetExtaData() throws IOException {
        final Map<String, Object> expected = extraData(0);

        final Node originalNode = RevObjectFactory.defaultInstance().createNode("feature-0",
                RevObjectTestSupport.hashString("1"), ObjectId.NULL, TYPE.FEATURE, null, expected);

        NodeSet nodeset = encodedDecode(Collections.singletonList(originalNode), TYPE.FEATURE);

        Node lazyNode = nodeset.build().get(0);
        Map<String, Object> extraData = lazyNode.getExtraData();
        assertEquals(expected, extraData);
    }

    @Test
    public void testLazyNodeGetExtaDataValue() throws IOException {
        final Map<String, Object> expected = extraData(0);

        final Node originalNode = RevObjectFactory.defaultInstance().createNode("feature-0",
                RevObjectTestSupport.hashString("1"), ObjectId.NULL, TYPE.FEATURE, null, expected);

        assertEquals(expected.size(), originalNode.getExtraData().size());
        assertEquals(expected, originalNode.getExtraData());

        NodeSet nodeset = encodedDecode(Collections.singletonList(originalNode), TYPE.FEATURE);

        Node lazyNode = nodeset.build().get(0);
        for (Map.Entry<String, Object> e : expected.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            Object lazyVal = lazyNode.getExtraData(key);
            assertEquals("key=" + key, value, lazyVal);
        }
    }

    private NodeSet encodedDecode(List<Node> nodes, TYPE type) throws IOException {
        final int offset = buff.size();
        NodeSet.encode(out, nodes, stringTable);
        byte[] rawData = buff.toByteArray();
        DataBuffer data = DataBuffer.wrap(rawData, stringTable);
        NodeSet decoded = NodeSet.decode(data, offset, type);

        assertEquals(type, decoded.getType());
        ImmutableList<Node> built = decoded.build();

        TestSupport.assertEqualsFully(nodes, built);

        return decoded;
    }

    private List<Node> featureNodes(int count) {
        return nodes(TYPE.FEATURE, count, true, true, false);
    }

    private List<Node> featureNodesWithExtraData(int count) {
        return nodes(TYPE.FEATURE, count, true, true, true);
    }

    private List<Node> treeNodes(int count) {
        return nodes(TYPE.TREE, count, true, true, false);
    }

    private List<Node> nodes(final TYPE type, final int count, boolean withMetadataId,
            boolean withBounds, boolean withExtraData) {
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name;
            ObjectId oid;
            ObjectId metadataId;
            Envelope bounds = null;
            Map<String, Object> extraData;

            name = "node-" + i;
            oid = RevObjectTestSupport.hashString(name);
            metadataId = withMetadataId ? RevObjectTestSupport.hashString(name + "md")
                    : ObjectId.NULL;
            if (withBounds) {
                boolean point = i % 2 == 0;
                double x = i / 1000d, y = i / 1000d;
                bounds = point ? new Envelope(x, x, y, y) : new Envelope(x, x + 1, y, y + 1);
            }

            extraData = withExtraData ? extraData(i) : null;

            Node node = RevObjectFactory.defaultInstance().createNode(name, oid, metadataId, type,
                    bounds, extraData);
            nodes.add(node);
        }
        return nodes;
    }

    private Map<String, Object> extraData(int i) {
        Map<String, Object> map = new HashMap<>();
        map.put("prop1", Integer.valueOf(i));
        map.put("prop2", "String prop value " + i);
        map.put("uuid", UUID.randomUUID());
        map.put("duplicatedValue", "same value for all");
        Geometry geom;
        try {
            geom = new WKTReader().read(String.format("LINESTRING(0 0, %d %d)", i, i));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
        map.put("mapProp", ImmutableMap.of("k1", "v1-" + i, //
                "k2", "v2-" + i, //
                "k3", Long.valueOf(i), //
                "nested-geom", geom));

        map.put("geom", geom);
        return map;
    }

}
