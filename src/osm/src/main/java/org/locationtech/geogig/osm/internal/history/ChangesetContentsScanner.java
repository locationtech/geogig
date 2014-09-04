/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal.history;

import static com.google.common.base.Optional.fromNullable;
import static com.google.common.base.Preconditions.checkArgument;
import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.locationtech.geogig.osm.internal.history.ParsingUtils.parseDateTime;

import java.io.InputStream;
import java.util.Iterator;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ImmutableSet;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

/**
 * Example changeset download:
 * 
 * <pre>
 * <code>
 * <?xml version="1.0" encoding="UTF-8"?>
 * <osmChange version="0.6" generator="Example" copyright="GeoGig contributors" attribution="http://geogig.org" license="http://geogig.org">
 *   <create>
 *     <node id="1234" lat="50.3" lon="16.0" changeset="1" user="fred" uid="1" visible="true" timestamp="2014-09-04T03:30:00Z" version="1">
 *       <tag k="name" v="Tag"/>
 *       <tag k="amenity" v="restaurant"/>
 *     </node>
 *   </create>
 * </osmChange>
 * </code>
 * </pre>
 */
class ChangesetContentsScanner {

    private static ImmutableSet<String> CHANGE_TAGS = ImmutableSet.of(
            Change.Type.create.toString(), Change.Type.modify.toString(),
            Change.Type.delete.toString());

    private static ImmutableSet<String> PRIMITIVE_TAGS = ImmutableSet.of("node", "way", "relation");

    private static final GeometryFactory GEOMFACT = new GeometryFactory();

    public Iterator<Change> parse(InputStream changesetDownloadStream) throws XMLStreamException {

        final XMLStreamReader reader;

        reader = XMLInputFactory.newFactory().createXMLStreamReader(changesetDownloadStream,
                "UTF-8");

        // position reader at first change, if any

        reader.nextTag();
        reader.require(START_ELEMENT, null, "osmChange");

        Iterator<Change> iterator = new AbstractIterator<Change>() {

            @Override
            protected Change computeNext() {
                Change next;
                try {
                    if (findNextChange(reader)) {
                        next = parseChange(reader);
                    } else {
                        return super.endOfData();
                    }
                } catch (XMLStreamException e) {
                    System.err.println("Error parsing change, ignoring and continuing "
                            + "with next change if possible. " + e.getMessage());
                    next = computeNext();
                }
                return next;
            }
        };

        return iterator;
    }

    private boolean findNextChange(XMLStreamReader reader) throws XMLStreamException {
        int eventType = reader.getEventType();
        do {
            if (eventType == START_ELEMENT) {
                String tag = reader.getLocalName();
                if (CHANGE_TAGS.contains(tag)) {
                    return true;
                }
            }

            reader.next();

            eventType = reader.getEventType();
        } while (eventType != END_DOCUMENT);
        return false;
    }

    /**
     * Example changeset:
     * 
     * <pre>
     * <code>
     * <?xml version="1.0" encoding="UTF-8"?>
     * <osmChange version="0.6" generator="OpenStreetMap server" copyright="OpenStreetMap and contributors" attribution="http://www.openstreetmap.org/copyright" license="http://opendatacommons.org/licenses/odbl/1-0/">
     *   <create>
     *     <relation id="4386" visible="true" timestamp="2009-11-21T09:02:09Z" user="jttt" uid="48" version="1" changeset="1864">
     *       <tag k="type" v="aoeua"/>
     *     </relation>
     *   </create>
     *   <modify>
     *     <relation id="4385" visible="true" timestamp="2009-11-21T09:05:32Z" user="jttt" uid="48" version="2" changeset="1864">
     *       <member type="relation" ref="4384" role=""/>
     *       <tag k="type" v="parent"/>
     *     </relation>
     *   </modify>
     *   <delete>
     *     <way id="49391" visible="false" timestamp="2009-11-21T09:05:32Z" user="jttt" uid="48" version="3" changeset="1864"/>
     *   </delete>
     *   <delete>
     *     <node id="1082496" changeset="1864" user="jttt" uid="48" visible="false" timestamp="2009-11-21T09:05:57Z" version="2"/>
     *   </delete>
     *   <create>
     *     <way id="49393" visible="true" timestamp="2009-11-21T09:12:53Z" user="jttt" uid="48" version="1" changeset="1864">
     *       <nd ref="1082500"/>
     *       <nd ref="1082501"/>
     *       <nd ref="1082502"/>
     *       <nd ref="1082503"/>
     *     </way>
     *   </create>
     *   <modify>
     *     <relation id="4394" visible="true" timestamp="2009-11-21T09:21:31Z" user="jttt" uid="48" version="2" changeset="1864">
     *       <member type="relation" ref="4393" role=""/>
     *       <member type="relation" ref="4395" role=""/>
     *       <member type="relation" ref="4396" role=""/>
     *       <tag k="eouaoeu" v="oeueaoeu"/>
     *     </relation>
     *   </modify>  <create>
     *     <relation id="4390" visible="true" timestamp="2009-11-21T09:12:53Z" user="jttt" uid="48" version="1" changeset="1864">
     *       <tag k="type" v="aeou"/>
     *     </relation>
     *   </create>
     *   <create>
     *     <relation id="4391" visible="true" timestamp="2009-11-21T09:12:53Z" user="jttt" uid="48" version="1" changeset="1864">
     *       <tag k="type" v="aoeuau"/>
     *     </relation>
     *   </create>
     *   <create>
     *     <relation id="4392" visible="true" timestamp="2009-11-21T09:13:55Z" user="jttt" uid="48" version="1" changeset="1864">
     *       <tag k="type" v="aeou"/>
     *     </relation>
     *   </create>
     *   <create>
     *     <relation id="4393" visible="true" timestamp="2009-11-21T09:18:06Z" user="jttt" uid="48" version="1" changeset="1864">
     *       <member type="way" ref="49393" role=""/>
     *       <tag k="type" v="aeua"/>
     *     </relation>
     *   </create>
     *   <create>
     *     <relation id="4394" visible="true" timestamp="2009-11-21T09:18:49Z" user="jttt" uid="48" version="1" changeset="1864">
     *       <member type="relation" ref="4393" role=""/>
     *       <tag k="eouaoeu" v="oeueaoeu"/>
     *     </relation>
     *   </create>
     * </osmChange>
     * </code>
     * </pre>
     */
    private Change parseChange(XMLStreamReader reader) throws XMLStreamException {
        reader.require(START_ELEMENT, null, null);

        final String changeName = reader.getLocalName();
        checkArgument(CHANGE_TAGS.contains(changeName));

        final Change.Type type = Change.Type.valueOf(reader.getLocalName());
        reader.nextTag();
        reader.require(START_ELEMENT, null, null);

        final String primitiveName = reader.getLocalName();

        checkArgument(PRIMITIVE_TAGS.contains(primitiveName));

        Primitive primitive = parsePrimitive(reader);

        reader.require(END_ELEMENT, null, primitiveName);
        reader.nextTag();
        reader.require(END_ELEMENT, null, changeName);

        Change change = new Change(type, primitive);
        return change;
    }

    Primitive parsePrimitive(XMLStreamReader reader) throws XMLStreamException {

        reader.require(START_ELEMENT, null, null);
        final String primitiveName = reader.getLocalName();
        checkArgument(PRIMITIVE_TAGS.contains(primitiveName));

        Primitive primitive = inferrPrimitive(reader);

        primitive.setId(Long.valueOf(reader.getAttributeValue(null, "id")));
        primitive.setVisible(Boolean.valueOf(reader.getAttributeValue(null, "visible")));
        primitive.setTimestamp(parseDateTime(reader.getAttributeValue(null, "timestamp")));
        primitive.setUserName(reader.getAttributeValue(null, "user"));

        Long uid = Long.valueOf(fromNullable(reader.getAttributeValue(null, "uid")).or("-1"));
        primitive.setUserId(uid);

        Integer version = Integer.valueOf(fromNullable(reader.getAttributeValue(null, "version"))
                .or("1"));
        primitive.setVersion(version);

        primitive.setChangesetId(Long.valueOf(reader.getAttributeValue(null, "changeset")));

        if (primitive instanceof Node) {
            Node node = (Node) primitive;
            String lat = reader.getAttributeValue(null, "lat");
            String lon = reader.getAttributeValue(null, "lon");
            // may be null in case of a delete change
            if (lat != null && lon != null) {
                double x = Double.valueOf(lon);
                double y = Double.valueOf(lat);
                Point location = GEOMFACT.createPoint(new Coordinate(x, y));
                node.setLocation(location);
            }
            parseNodeContents(node, reader);
        } else if (primitive instanceof Way) {
            Way way = (Way) primitive;
            parseWayContents(way, reader);
        } else {
            Relation relation = (Relation) primitive;
            parseRelationContents(relation, reader);
        }

        reader.require(END_ELEMENT, null, primitiveName);
        return primitive;
    }

    /**
     * @param node
     * @param reader
     * @throws XMLStreamException
     */
    private void parseNodeContents(Node node, XMLStreamReader reader) throws XMLStreamException {
        while (true) {
            int tag = reader.next();

            if (tag == END_ELEMENT) {
                String tagName = reader.getLocalName();
                if (tagName.equals("node")) {
                    break;
                }
            } else if (tag == START_ELEMENT) {
                String tagName = reader.getLocalName();
                if ("tag".equals(tagName)) {
                    parseTag(node, reader);
                    reader.require(END_ELEMENT, null, "tag");
                }
            } else if (tag == END_DOCUMENT) {
                throw new IllegalStateException("premature end of document");
            }
        }

        reader.require(END_ELEMENT, null, "node");
    }

    /**
     * @param way
     * @param reader
     * @throws XMLStreamException
     */
    private void parseWayContents(Way way, XMLStreamReader reader) throws XMLStreamException {
        reader.require(START_ELEMENT, null, "way");
        while (true) {
            int tag = reader.next();

            if (tag == END_ELEMENT) {
                String tagName = reader.getLocalName();
                if (tagName.equals("way")) {
                    break;
                }
            } else if (tag == START_ELEMENT) {
                String tagName = reader.getLocalName();
                if ("tag".equals(tagName)) {
                    parseTag(way, reader);
                    reader.require(END_ELEMENT, null, "tag");
                } else if ("nd".equals(tagName)) {
                    long nodeRef = Long.valueOf(reader.getAttributeValue(null, "ref"));
                    reader.nextTag();
                    reader.require(END_ELEMENT, null, "nd");
                    way.addNode(nodeRef);
                }
            } else if (tag == END_DOCUMENT) {
                throw new IllegalStateException("premature end of document");
            }
        }

        reader.require(END_ELEMENT, null, "way");
    }

    /**
     * @param relation
     * @param reader
     * @throws XMLStreamException
     */
    private void parseRelationContents(Relation relation, XMLStreamReader reader)
            throws XMLStreamException {
        reader.require(START_ELEMENT, null, "relation");
        while (true) {
            int tag = reader.next();

            if (tag == END_ELEMENT) {
                String tagName = reader.getLocalName();
                if (tagName.equals("relation")) {
                    break;
                }
            } else if (tag == START_ELEMENT) {
                String tagName = reader.getLocalName();
                if ("tag".equals(tagName)) {
                    parseTag(relation, reader);
                    reader.require(END_ELEMENT, null, "tag");
                } else if ("member".equals(tagName)) {
                    String type = reader.getAttributeValue(null, "type");
                    long ref = Long.valueOf(reader.getAttributeValue(null, "ref"));
                    String role = reader.getAttributeValue(null, "role");
                    if ("".equals(role)) {
                        role = null;
                    }
                    reader.nextTag();
                    reader.require(END_ELEMENT, null, "member");

                    Relation.Member member = new Relation.Member(type, ref, role);
                    relation.addMember(member);
                }
            } else if (tag == END_DOCUMENT) {
                throw new IllegalStateException("premature end of document");
            }
        }

        reader.require(END_ELEMENT, null, "relation");
    }

    private void parseTag(Primitive primitive, XMLStreamReader reader) throws XMLStreamException {
        reader.require(START_ELEMENT, null, "tag");
        String key = reader.getAttributeValue(null, "k");
        String value = reader.getAttributeValue(null, "v");
        primitive.getTags().put(key, value);
        reader.nextTag();
        reader.require(END_ELEMENT, null, "tag");
    }

    /**
     * @param reader
     * @return
     */
    private Primitive inferrPrimitive(XMLStreamReader reader) {
        final String primitiveName = reader.getLocalName();
        if ("node".equals(primitiveName)) {
            return new Node();
        } else if ("way".equals(primitiveName)) {
            return new Way();
        } else if ("relation".equals(primitiveName)) {
            return new Relation();
        }
        throw new IllegalArgumentException("Unknown primitive tag: " + primitiveName);
    }

}
