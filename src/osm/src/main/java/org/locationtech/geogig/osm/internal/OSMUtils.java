/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.osm.internal;

import java.util.Collection;
import java.util.Iterator;

import javax.annotation.Nullable;

import org.geotools.data.DataUtilities;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

public class OSMUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(OSMUtils.class);

    public static final String OSM_FETCH_BRANCH = "OSM_FETCH";

    public static final String DEFAULT_API_ENDPOINT = "http://overpass-api.de/api/interpreter";

    public static final String FR_API_ENDPOINT = "http://api.openstreetmap.fr/oapi/interpreter/";

    public static final String RU_API_ENDPOINT = "http://overpass.osm.rambler.ru/";

    public static final String NODE_TYPE_NAME = "node";

    public static final String WAY_TYPE_NAME = "way";

    public static final String NAMESPACE = "www.openstreetmap.org";

    private static SimpleFeatureType NodeType;

    public synchronized static SimpleFeatureType nodeType() {
        if (NodeType == null) {
            String typeSpec = "visible:Boolean,version:Integer,timestamp:java.lang.Long,tags:String,"
                    + "changeset:java.lang.Long,user:String,location:Point:srid=4326";
            try {
                SimpleFeatureType type = DataUtilities.createType(NAMESPACE,
                        OSMUtils.NODE_TYPE_NAME, typeSpec);
                boolean longitudeFirst = true;
                CoordinateReferenceSystem forceLonLat = CRS.decode("EPSG:4326", longitudeFirst);
                NodeType = DataUtilities.createSubType(type, null, forceLonLat);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        return NodeType;
    }

    private static SimpleFeatureType WayType;

    public synchronized static SimpleFeatureType wayType() {
        if (WayType == null) {
            String typeSpec = "visible:Boolean,version:Integer,timestamp:java.lang.Long,tags:String,"
                    + "changeset:java.lang.Long,user:String,nodes:String,way:LineString:srid=4326";
            try {
                SimpleFeatureType type = DataUtilities.createType(NAMESPACE,
                        OSMUtils.WAY_TYPE_NAME, typeSpec);
                boolean longitudeFirst = true;
                CoordinateReferenceSystem forceLonLat = CRS.decode("EPSG:4326", longitudeFirst);
                WayType = DataUtilities.createSubType(type, null, forceLonLat);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        return WayType;
    }

    /**
     * @param collection
     * @return
     */
    @Nullable
    public static String buildTagsString(Collection<Tag> collection) {
        if (collection.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Iterator<Tag> it = collection.iterator(); it.hasNext();) {
            Tag e = it.next();
            String key = e.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }
            String value = e.getValue();
            sb.append(key).append(':').append(value);
            if (it.hasNext()) {
                sb.append('|');
            }
        }
        return sb.toString();
    }

    public static Collection<Tag> buildTagsCollectionFromString(String tagsString) {
        Collection<Tag> tags = Lists.newArrayList();
        if (tagsString != null) {
            String[] tokens = tagsString.split("\\|");
            for (String token : tokens) {
                int idx = token.lastIndexOf(':');
                if (idx != -1) {
                    Tag tag = new Tag(token.substring(0, idx), token.substring(idx + 1));
                    tags.add(tag);
                } else {
                    LOGGER.info("found tag token '{}' with no value in tagString '{}'", token,
                            tagsString);
                }
            }
        }
        return tags;
    }
}
