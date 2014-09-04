/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.Node;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.Platform;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevFeatureTypeImpl;
import org.locationtech.geogig.api.RevObject.TYPE;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.RevTreeBuilder;
import org.locationtech.geogig.storage.ObjectDatabase;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.geometry.BoundingBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vividsolutions.jts.geom.Envelope;

class RevTreeBuilder2 {

    private static final Logger LOGGER = LoggerFactory.getLogger(RevTreeBuilder2.class);

    private final NodeIndex nodeIndex;

    private final ObjectDatabase db;

    private final RevTree original;

    private final Map<Name, RevFeatureType> revFeatureTypes = Maps.newConcurrentMap();

    private final ObjectId defaultMetadataId;

    /**
     * Copy constructor
     */
    public RevTreeBuilder2(final ObjectDatabase db, @Nullable final RevTree origTree,
            final ObjectId defaultMetadataId, final Platform platform,
            final ExecutorService executorService) {

        this.db = db;
        this.original = origTree;
        this.defaultMetadataId = defaultMetadataId;
        this.nodeIndex = new FileNodeIndex(platform, executorService);
    }

    public ObjectId getDefaultMetadataId() {
        return defaultMetadataId;
    }

    /**
     * Adds or replaces an element in the tree with the given key.
     * <p>
     * <!-- Implementation detail: If the number of cached entries (entries held directly by this
     * tree) reaches {@link #DEFAULT_NORMALIZATION_THRESHOLD}, this tree will {@link #normalize()}
     * itself.
     * 
     * -->
     * 
     * @param key non null
     * @param value non null
     */
    public synchronized RevTreeBuilder2 put(final Node node) {
        Preconditions.checkNotNull(node, "node can't be null");
        nodeIndex.add(node);
        return this;
    }

    /**
     * Traverses the nodes in the {@link NodeIndex}, deletes the ones with {@link ObjectId#NULL
     * NULL} ObjectIds, and adds the ones with non "NULL" ids.
     * 
     * @return the new tree, not saved to the object database. Any bucket tree though is saved when
     *         this method returns.
     */
    public RevTree build() {
        if (nodeIndex == null) {
            return original.builder(db).build();
        }

        Stopwatch sw = Stopwatch.createStarted();
        RevTreeBuilder builder;
        try {
            builder = new RevTreeBuilder(db, original);
            Iterator<Node> nodes = nodeIndex.nodes();
            while (nodes.hasNext()) {
                Node node = nodes.next();
                if (node.getObjectId().isNull()) {
                    builder.remove(node.getName());
                } else {
                    builder.put(node);
                }
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        } finally {
            nodeIndex.close();
        }
        LOGGER.debug("Index traversed in {}", sw.stop());
        sw.reset().start();

        RevTree namedTree = builder.build();
        saveExtraFeatureTypes();
        LOGGER.debug("RevTreeBuilder.build() in {}", sw.stop());
        return namedTree;
    }

    private void saveExtraFeatureTypes() {
        Collection<RevFeatureType> types = revFeatureTypes.values();
        List<RevFeatureType> nonDefaults = Lists.newLinkedList();
        for (RevFeatureType t : types) {
            if (!t.getId().equals(defaultMetadataId)) {
                nonDefaults.add(t);
            }
        }
        if (!nonDefaults.isEmpty()) {
            db.putAll(nonDefaults.iterator());
        }
    }

    public Node putFeature(final ObjectId id, final String name,
            @Nullable final BoundingBox bounds, final FeatureType type) {
        Envelope bbox;
        if (bounds == null) {
            bbox = null;
        } else if (bounds instanceof Envelope) {
            bbox = (Envelope) bounds;
        } else {
            bbox = new Envelope(bounds.getMinimum(0), bounds.getMaximum(0), bounds.getMinimum(1),
                    bounds.getMaximum(1));
        }
        RevFeatureType revFeatureType = revFeatureTypes.get(type.getName());
        if (null == revFeatureType) {
            revFeatureType = RevFeatureTypeImpl.build(type);
            revFeatureTypes.put(type.getName(), revFeatureType);
        }
        ObjectId metadataId = revFeatureType.getId().equals(defaultMetadataId) ? ObjectId.NULL
                : revFeatureType.getId();
        Node node = Node.create(name, id, metadataId, TYPE.FEATURE, bbox);
        put(node);
        return node;
    }

    /**
     * Marks the node named after {@code fid} to be deleted by adding a Node with
     * {@link ObjectId#NULL NULL} ObjectId
     */
    public void removeFeature(String fid) {
        Node node = Node.create(fid, ObjectId.NULL, ObjectId.NULL, TYPE.FEATURE, null);
        put(node);
    }

}
