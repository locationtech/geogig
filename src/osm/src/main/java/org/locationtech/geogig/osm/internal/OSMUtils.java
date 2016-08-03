/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataUtilities;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.geogig.model.FieldType;
import org.locationtech.geogig.repository.ProgressListener;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.vividsolutions.jts.geom.LineString;

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
            String typeSpec = "visible:Boolean,version:Integer,timestamp:java.lang.Long,tags:java.util.Map,"
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
            SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
            builder.add("visible", Boolean.class);
            builder.add("version", Integer.class);
            builder.add("timestamp", Long.class);
            builder.add("tags", java.util.Map.class);
            builder.add("changeset", Long.class);
            builder.add("user", String.class);
            builder.add("nodes", long[].class);
            try {
                builder.add("way", LineString.class, CRS.decode("EPSG:4326", true));
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
            // String typeSpec =
            // "visible:Boolean,version:Integer,timestamp:java.lang.Long,tags:String,"
            // + "changeset:java.lang.Long,user:String,nodes:String,way:LineString:srid=4326";
            try {
                // SimpleFeatureType type = DataUtilities.createType(NAMESPACE,
                // OSMUtils.WAY_TYPE_NAME, typeSpec);
                // boolean longitudeFirst = true;
                // CoordinateReferenceSystem forceLonLat = CRS.decode("EPSG:4326", longitudeFirst);
                // WayType = DataUtilities.createSubType(type, null, forceLonLat);
                builder.setNamespaceURI(NAMESPACE);
                builder.setName(WAY_TYPE_NAME);
                WayType = builder.buildFeatureType();
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
        }
        return WayType;
    }

    private static final Ordering<Tag> TAG_KEY_ORDER = new Ordering<Tag>() {

        @Override
        public int compare(Tag left, Tag right) {
            return Ordering.natural().compare(left.getKey(), right.getKey());
        }
    };

    public static long[] buildNodesArray(List<WayNode> wayNodes) {
        long[] nodeIds = new long[wayNodes.size()];
        for (int i = 0; i < wayNodes.size(); i++) {
            WayNode node = wayNodes.get(i);
            nodeIds[i] = node.getNodeId();
        }
        return nodeIds;
    }

    @Nullable
    public static Map<String, String> buildTagsMap(Iterable<Tag> collection) {
        Map<String, String> tags = Maps.newHashMap();
        String key;
        String value;
        for (Tag e : collection) {
            key = e.getKey();
            if (key == null || key.isEmpty()) {
                continue;
            }
            value = e.getValue();
            tags.put(key, value);
        }
        return tags.isEmpty() ? null : tags;
    }

    public static Collection<Tag> buildTagsCollection(@Nullable Map<String, String> map) {
        Collection<Tag> tags = Lists.newArrayList();
        if (map != null) {
            for (Entry<String, String> e : map.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                Tag tag = new Tag(k, v);
                tags.add(tag);
            }
        }
        return tags;
    }

    /**
     * @return {@code featureType} if it doesn't contain any array or Map attribute, otherwise a new
     *         feature type with any array or Map attribute replaced by another with the same name
     *         but bound to {@code String.class}
     */
    public static SimpleFeatureType adaptIncompatibleAttributesForExport(
            SimpleFeatureType featureType, ProgressListener progressListener) {

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.init(featureType);
        for (int i = 0; i < featureType.getAttributeCount(); i++) {
            AttributeDescriptor attribute = builder.get(i);
            Class<?> binding = attribute.getType().getBinding();
            String name = attribute.getLocalName();
            FieldType fieldType = FieldType.forBinding(binding);
            if (binding.isArray() || Map.class.isAssignableFrom(binding)) {
                progressListener.setDescription(String.format(
                        "Attribute '%s' will be written as String, target doesn't support type %s",
                        name, fieldType));

                AttributeType type = builder.getFeatureTypeFactory()
                        .createAttributeType(attribute.getType().getName(), String.class, false,
                                false, null, null, null);
                AttributeDescriptor descriptor = builder.getFeatureTypeFactory()
                        .createAttributeDescriptor(type, type.getName(), 0, 1, true, null);

                // builder.remove(name);
                builder.set(i, descriptor);
            }
        }
        return builder.buildFeatureType();
    }
}
