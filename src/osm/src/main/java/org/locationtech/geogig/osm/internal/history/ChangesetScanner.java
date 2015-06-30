/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal.history;

import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.InputStream;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.eclipse.jdt.annotation.Nullable;

import com.vividsolutions.jts.geom.Envelope;

/**
 * Example changeset:
 * 
 * <pre>
 * <code>
 *  <?xml version="1.0" encoding="UTF-8"?>
 *  <osm version="0.6" generator="OpenStreetMap server" copyright="OpenStreetMap and contributors" attribution="http://www.openstreetmap.org/copyright" license="http://opendatacommons.org/licenses/odbl/1-0/">
 *    <changeset id="1100" user="BMO_2009" uid="26" created_at="2009-10-10T20:02:09Z" closed_at="2009-10-10T20:02:21Z" open="false" min_lat="48.4031818" min_lon="-4.4631203" max_lat="48.4058698" max_lon="-4.4589401">
 *      <tag k="created_by" v="bulk_upload.py/17742 Python/2.5.2"/>
 *      <tag k="comment" v="second test upload of BMO data - see http://wiki.openstreetmap.org/wiki/BMO"/>
 *    </changeset>
 *  </osm>
 * </code>
 * </pre>
 */
class ChangesetScanner {

    private XMLStreamReader reader;

    public ChangesetScanner(InputStream changesetStream) throws XMLStreamException,
            FactoryConfigurationError {

        this.reader = XMLInputFactory.newFactory().createXMLStreamReader(changesetStream, "UTF-8");

        reader.nextTag();
        reader.require(START_ELEMENT, null, "osm");
        reader.nextTag();
    }

    @Nullable
    public Changeset parseNext() throws XMLStreamException {
        if (reader.getEventType() == END_ELEMENT && reader.getLocalName().equals("osm")) {
            return null;
        }
        Changeset changeset = parse(reader);

        return changeset;
    }

    /**
     * Example changeset:
     * 
     * <pre>
     * <code>
     *  <?xml version="1.0" encoding="UTF-8"?>
     *  <osm version="0.6" generator="OpenStreetMap server" copyright="OpenStreetMap and contributors" attribution="http://www.openstreetmap.org/copyright" license="http://opendatacommons.org/licenses/odbl/1-0/">
     *    <changeset id="1100" user="BMO_2009" uid="26" created_at="2009-10-10T20:02:09Z" closed_at="2009-10-10T20:02:21Z" open="false" min_lat="48.4031818" min_lon="-4.4631203" max_lat="48.4058698" max_lon="-4.4589401">
     *      <tag k="created_by" v="bulk_upload.py/17742 Python/2.5.2"/>
     *      <tag k="comment" v="second test upload of BMO data - see http://wiki.openstreetmap.org/wiki/BMO"/>
     *    </changeset>
     *  </osm>
     * </code>
     * </pre>
     */
    private Changeset parse(XMLStreamReader reader) throws XMLStreamException {
        reader.require(START_ELEMENT, null, "changeset");

        Changeset changeset = new Changeset();
        changeset.setId(Long.valueOf(reader.getAttributeValue(null, "id")));
        changeset.setUserName(reader.getAttributeValue(null, "user"));

        String uid = reader.getAttributeValue(null, "uid");
        if (uid != null) {
            changeset.setUserId(Long.valueOf(uid));
        }

        changeset.setOpen(Boolean.valueOf(reader.getAttributeValue(null, "open")));

        changeset.setCreated(ParsingUtils.parseDateTime(reader
                .getAttributeValue(null, "created_at")));
        if (!changeset.isOpen()) {
            changeset.setClosed(ParsingUtils.parseDateTime(reader.getAttributeValue(null,
                    "closed_at")));
        }
        changeset.setWgs84Bounds(parseWGS84Bounds(reader));

        while (true) {
            int tag = reader.next();

            if (tag == END_ELEMENT) {
                String tagName = reader.getLocalName();
                if (tagName.equals("changeset")) {
                    break;
                }
            } else if (tag == START_ELEMENT) {
                String tagName = reader.getLocalName();
                if ("tag".equals(tagName)) {
                    String key = reader.getAttributeValue(null, "k");
                    String value = reader.getAttributeValue(null, "v");
                    if ("comment".equals(key)) {
                        changeset.setComment(value);
                    } else if (key != null && value != null) {
                        changeset.getTags().put(key, value);
                    }
                }
            } else if (tag == END_DOCUMENT) {
                throw new IllegalStateException("premature end of document");
            }
        }

        reader.require(END_ELEMENT, null, "changeset");
        reader.nextTag();
        return changeset;
    }

    /**
     * Extracts bounds from:
     * 
     * <pre>
     * <code>
     *  <changeset min_lat="48.4031818" min_lon="-4.4631203" max_lat="48.4058698" max_lon="-4.4589401">
     * </code>
     * </pre>
     */
    private static @Nullable Envelope parseWGS84Bounds(XMLStreamReader reader) {

        String minLat = reader.getAttributeValue(null, "min_lat");
        String minLon = reader.getAttributeValue(null, "min_lon");
        String maxLat = reader.getAttributeValue(null, "max_lat");
        String maxLon = reader.getAttributeValue(null, "max_lon");
        if (minLat == null || minLon == null || maxLat == null || maxLon == null) {
            return null;
        }

        return ParsingUtils.parseWGS84Bounds(minLat, minLon, maxLat, maxLon);
    }
}
