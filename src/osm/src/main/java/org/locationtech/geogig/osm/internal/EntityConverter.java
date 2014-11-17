/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.osm.internal;

import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.opengis.feature.FeatureFactory;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.OsmUser;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;

import com.google.common.collect.Lists;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

public class EntityConverter {

    /** Cached instance to avoid multiple factory lookups */
    private static final FeatureFactory FEATURE_FACTORY = CommonFactoryFinder
            .getFeatureFactory(null);

    public SimpleFeature toFeature(Entity entity, Geometry geom) {

        SimpleFeatureType ft = entity instanceof Node ? OSMUtils.nodeType() : OSMUtils.wayType();
        SimpleFeatureBuilder builder = new SimpleFeatureBuilder(ft, FEATURE_FACTORY);

        builder.set("visible", Boolean.TRUE); // TODO: Check this!
        builder.set("version", Integer.valueOf(entity.getVersion()));
        builder.set("timestamp", Long.valueOf(entity.getTimestamp().getTime()));
        builder.set("changeset", Long.valueOf(entity.getChangesetId()));
        String tags = OSMUtils.buildTagsString(entity.getTags());
        builder.set("tags", tags);
        String user = entity.getUser().getName() + ":" + Integer.toString(entity.getUser().getId());
        builder.set("user", user);
        if (entity instanceof Node) {
            builder.set("location", geom);
        } else if (entity instanceof Way) {
            builder.set("way", geom);
            String nodes = buildNodesString(((Way) entity).getWayNodes());
            builder.set("nodes", nodes);
        } else {
            throw new IllegalArgumentException();
        }

        String fid = String.valueOf(entity.getId());
        SimpleFeature simpleFeature = builder.buildFeature(fid);
        return simpleFeature;
    }

    protected String buildNodesString(List<WayNode> wayNodes) {
        StringBuilder sb = new StringBuilder();
        for (Iterator<WayNode> it = wayNodes.iterator(); it.hasNext();) {
            WayNode node = it.next();
            sb.append(Long.toString(node.getNodeId()));
            if (it.hasNext()) {
                sb.append(";");
            }
        }
        return sb.toString();

    }

    /**
     * Converts a Feature to a OSM Entity
     * 
     * @param feature the feature to convert
     * @param replaceId. The changesetId to use in case the feature has a negative one indicating a
     *        temporary value
     * @return
     */
    public Entity toEntity(SimpleFeature feature, Long changesetId) {
        Entity entity;
        SimpleFeatureType type = feature.getFeatureType();
        long id = Long.parseLong(feature.getID());
        int version = ((Integer) feature.getAttribute("version")).intValue();
        Long changeset = (Long) feature.getAttribute("changeset");
        if (changesetId != null && changeset < 0) {
            changeset = changesetId;
        }
        Long milis = (Long) feature.getAttribute("timestamp");
        Date timestamp = new Date(milis);
        String user = (String) feature.getAttribute("user");
        String[] userTokens = user.split(":");
        OsmUser osmuser;
        try {
            osmuser = new OsmUser(Integer.parseInt(userTokens[1]), userTokens[0]);
        } catch (Exception e) {
            osmuser = OsmUser.NONE;
        }
        String tagsString = (String) feature.getAttribute("tags");
        Collection<Tag> tags = OSMUtils.buildTagsCollectionFromString(tagsString);

        CommonEntityData entityData = new CommonEntityData(id, version, timestamp, osmuser,
                changeset, tags);
        if (type.equals(OSMUtils.nodeType())) {
            Point pt = (Point) feature.getDefaultGeometryProperty().getValue();
            entity = new Node(entityData, pt.getY(), pt.getX());

        } else {
            List<WayNode> nodes = Lists.newArrayList();
            String nodesString = (String) feature.getAttribute("nodes");
            for (String s : nodesString.split(";")) {
                nodes.add(new WayNode(Long.parseLong(s)));
            }
            entity = new Way(entityData, nodes);
        }

        return entity;
    }

}
