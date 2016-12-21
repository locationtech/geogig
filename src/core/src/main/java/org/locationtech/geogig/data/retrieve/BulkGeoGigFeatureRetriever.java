/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Blasby (Boundless) - initial implementation
 */

package org.locationtech.geogig.data.retrieve;

import static org.locationtech.geogig.storage.BulkOpListener.NOOP_LISTENER;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.storage.ObjectDatabase;

import com.google.common.base.Function;
import org.locationtech.geogig.repository.FeatureInfo;

/**
 * This class takes a list of NodeRef (i.e. from the leaf nodes of ObjectIDs that point to features)
 * and gets the underlying feature serialized objects from the object database.
 * 
 * This returns a FeatureInfo, which correlates the requested NodeRef (which has a
 * path/feature-name/feature-type-metadata-id) to the RevFeature (which has the actual serialized
 * data - attributes).
 * 
 * This provides non-geotools access to the feature data.
 */
public class BulkGeoGigFeatureRetriever implements Function<List<NodeRef>, Iterator<FeatureInfo>> {
    ObjectDatabase odb;

    public BulkGeoGigFeatureRetriever(ObjectDatabase odb) {
        this.odb = odb;
    }

    public Iterator<FeatureInfo> apply(List<NodeRef> refs) {
        Map<ObjectId, FeatureInfo> correlationIndex = new HashMap<ObjectId, FeatureInfo>(
                refs.size());

        Map<ObjectId, NodeRef> indexRefs = new HashMap<ObjectId, NodeRef>(refs.size());

        for (NodeRef ref : refs) {
            indexRefs.put(ref.getObjectId(), ref);
        }
        Iterable<ObjectId> ids = correlationIndex.keySet();
        Iterator<RevFeature> all = odb.getAll(ids, NOOP_LISTENER, RevFeature.class);
        while (all.hasNext()) {
            RevFeature f = all.next();
            NodeRef ref = indexRefs.get(f.getId());
            correlationIndex.put(f.getId(), FeatureInfo.insert(f, ref.getMetadataId(), ref.path()));
        }

        return correlationIndex.values().iterator();
    }
}
