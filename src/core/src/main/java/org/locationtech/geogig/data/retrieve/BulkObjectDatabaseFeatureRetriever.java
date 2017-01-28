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

import java.util.*;

import com.google.common.base.Function;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;
import org.locationtech.geogig.repository.FeatureInfo;
import org.locationtech.geogig.repository.NodeRef;
import org.locationtech.geogig.storage.ObjectDatabase;


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
public class BulkObjectDatabaseFeatureRetriever implements Function<List<NodeRef>, Iterator<FeatureInfo>> {
    ObjectDatabase odb;

    public BulkObjectDatabaseFeatureRetriever(ObjectDatabase odb) {
        this.odb = odb;
    }

    public Iterator<FeatureInfo> apply(List<NodeRef> refs) {
        Map<ObjectId, RevFeature> correlationIndex =
                new HashMap<>(refs.size());

        ArrayList<ObjectId> ids = new ArrayList<>(refs.size());
        refs.forEach(ref->ids.add(ref.getObjectId()));


        Iterator<RevFeature> all = odb.getAll(ids, NOOP_LISTENER, RevFeature.class);
        all.forEachRemaining(revFeature -> correlationIndex.put(revFeature.getId(),revFeature));


        ArrayList<FeatureInfo> result = new ArrayList<>(refs.size());
        refs.forEach( ref-> result.add(FeatureInfo.insert(correlationIndex.get(ref.getObjectId()), ref.getMetadataId(), ref.path()    )));

        return result.iterator();
    }
}
