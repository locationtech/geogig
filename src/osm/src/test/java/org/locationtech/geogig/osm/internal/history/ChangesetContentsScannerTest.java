/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/

package org.locationtech.geogig.osm.internal.history;

import static org.locationtech.geogig.osm.internal.history.Change.Type.create;
import static org.locationtech.geogig.osm.internal.history.Change.Type.delete;
import static org.locationtech.geogig.osm.internal.history.Change.Type.modify;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nullable;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.junit.Assert;
import org.junit.Test;
import org.locationtech.geogig.osm.internal.history.Relation.Member;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 *
 */
public class ChangesetContentsScannerTest extends Assert {

    @Test
    public void testChange1100() throws XMLStreamException {
        Iterator<Change> iterator = parse("1100/download.xml");
        ArrayList<Change> list = Lists.newArrayList(iterator);
        assertEquals(100, list.size());
    }

    @Test
    public void testChange1624() throws XMLStreamException {
        Iterator<Change> iterator = parse("1624/download.xml");
        ArrayList<Change> list = Lists.newArrayList(iterator);
        assertEquals(4, list.size());
        assertEquals(create, list.get(0).getType());
        assertEquals(modify, list.get(1).getType());
        assertEquals(modify, list.get(2).getType());
        assertEquals(delete, list.get(3).getType());
        assertTrue(list.get(0).getNode().isPresent());
        assertTrue(list.get(1).getNode().isPresent());
        assertTrue(list.get(2).getNode().isPresent());
        assertTrue(list.get(3).getNode().isPresent());
    }

    @Test
    public void testChange1864() throws XMLStreamException {
        Iterator<Change> iterator = parse("1864/download.xml");
        ArrayList<Change> list = Lists.newArrayList(iterator);
        assertEquals(22, list.size());
        assertEquals(create, list.get(0).getType());
        assertEquals(create, list.get(1).getType());
        assertEquals(modify, list.get(2).getType());
        assertEquals(delete, list.get(3).getType());

        assertTrue(list.get(0).getRelation().isPresent());
        assertTrue(list.get(1).getRelation().isPresent());
        assertTrue(list.get(2).getRelation().isPresent());
        assertTrue(list.get(3).getWay().isPresent());
        assertTrue(list.get(4).getNode().isPresent());
    }

    @Test
    public void testParseNodeNoLocation() throws Exception {
        String nodeDef = "<node id=\"1082496\" changeset=\"1864\" user=\"jttt\" uid=\"48\" visible=\"false\" timestamp=\"2009-11-21T09:05:57Z\" version=\"2\"/>";
        Node node = (Node) new ChangesetContentsScanner().parsePrimitive(reader(nodeDef));
        assertPrimitive(node, 1082496L, 1864, "jttt", 48, false, "2009-11-21T09:05:57Z", 2, null);
        assertFalse(node.getLocation().isPresent());
    }

    @Test
    public void testParseNode() throws Exception {
        String nodeDef = "<node id=\"1082496\" lat=\"48.4056473\" lon=\"-4.4621888\"  changeset=\"1864\" user=\"jttt\" uid=\"48\" visible=\"false\" timestamp=\"2009-11-21T09:05:57Z\" version=\"2\"/>";
        Node node = (Node) new ChangesetContentsScanner().parsePrimitive(reader(nodeDef));
        assertPrimitive(node, 1082496L, 1864, "jttt", 48, false, "2009-11-21T09:05:57Z", 2, null);

        assertTrue(node.getLocation().isPresent());

        assertEquals(-4.4621888, node.getLocation().get().getX(), 1E-6);
        assertEquals(48.4056473, node.getLocation().get().getY(), 1E-6);
    }

    @Test
    public void testParseWay() throws Exception {
        String wayDef = "<way id=\"49393\" visible=\"true\" timestamp=\"2009-11-21T09:12:53Z\" user=\"jttt\" uid=\"48\" version=\"1\" changeset=\"1864\">"
                + " <nd ref=\"1082500\"/>"//
                + " <nd ref=\"1082501\"/>"//
                + " <nd ref=\"1082502\"/>"//
                + " <nd ref=\"1082503\"/>"//
                + " <tag k=\"type\" v=\"road\" />"//
                + "</way>";

        Way way = (Way) new ChangesetContentsScanner().parsePrimitive(reader(wayDef));
        assertPrimitive(way, 49393L, 1864, "jttt", 48, true, "2009-11-21T09:12:53Z", 1,
                ImmutableMap.of("type", "road"));

        assertEquals(ImmutableList.of(1082500L, 1082501L, 1082502L, 1082503L), way.getNodes());
    }

    @Test
    public void testParseRelation() throws Exception {
        String relationDef = "<relation id=\"4394\" visible=\"true\" timestamp=\"2009-11-21T09:21:31Z\" user=\"jttt\" uid=\"48\" version=\"2\" changeset=\"1864\">"//
                + " <member type=\"relation\" ref=\"4393\" role=\"\"/>"//
                + " <member type=\"relation\" ref=\"4395\" role=\"testRole1\"/>"//
                + " <member type=\"relation\" ref=\"4396\" role=\"testRole2\"/>"//
                + " <tag k=\"eouaoeu\" v=\"oeueaoeu\"/>"//
                + "</relation>";

        Relation rel = (Relation) new ChangesetContentsScanner()
                .parsePrimitive(reader(relationDef));
        assertPrimitive(rel, 4394L, 1864, "jttt", 48, true, "2009-11-21T09:21:31Z", 2,
                ImmutableMap.of("eouaoeu", "oeueaoeu"));

        assertEquals(3, rel.getMembers().size());
        assertMember(rel.getMembers().get(0), "relation", 4393, null);
        assertMember(rel.getMembers().get(1), "relation", 4395, "testRole1");
        assertMember(rel.getMembers().get(2), "relation", 4396, "testRole2");
    }

    private void assertMember(Member member, String type, int ref, String role) {
        assertEquals(type, member.getType());
        assertEquals(ref, member.getRef());
        if (role == null) {
            assertFalse(member.getRole().isPresent());
        } else {
            assertEquals(role, member.getRole().get());
        }
    }

    private void assertPrimitive(Primitive p, long id, long changesetid, String user, int uid,
            boolean visible, String timestamp, int version, @Nullable Map<String, String> tags) {
        assertEquals(id, p.getId());
        assertEquals(changesetid, p.getChangesetId());
        assertEquals(user, p.getUserName());
        assertEquals(uid, p.getUserId());
        assertEquals(visible, p.isVisible());

        long t = ParsingUtils.parseDateTime(timestamp);
        assertEquals(t, p.getTimestamp());
        assertEquals(version, p.getVersion());
        if (tags == null) {
            assertTrue(p.getTags().isEmpty());
        } else {
            assertEquals(tags, p.getTags());
        }
    }

    private XMLStreamReader reader(String node) throws Exception {
        ByteArrayInputStream in = new ByteArrayInputStream(node.getBytes("UTF-8"));
        XMLStreamReader reader = XMLInputFactory.newFactory().createXMLStreamReader(in);
        reader.nextTag();
        return reader;
    }

    private Iterator<Change> parse(String resource) throws XMLStreamException {
        InputStream in = getClass().getResourceAsStream(resource);
        assertNotNull(in);
        return new ChangesetContentsScanner().parse(in);
    }
}
