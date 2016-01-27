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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.FeatureBuilder;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureTypeImpl;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.LsTreeOp;
import org.locationtech.geogig.api.plumbing.LsTreeOp.Strategy;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.osm.internal.log.OSMMappingLogEntry;
import org.locationtech.geogig.osm.internal.log.WriteOSMMappingEntries;
import org.opengis.feature.Feature;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;

/**
 * Creates new data in a geogig repository, based on the current OSM data in the repository and a
 * mapping that defines the schema to use for creating new features and the destination trees.
 * 
 * The source data used is the working tree data in the "node" and "way" trees.
 * 
 */
public class OSMMapOp extends AbstractGeoGigOp<RevTree> {

    /**
     * The mapping to use
     */
    private Mapping mapping;

    /**
     * The message to use for the commit to create
     */
    private String message;

    /**
     * Sets the mapping to use
     * 
     * @param mapping the mapping to use
     * @return {@code this}
     */
    public OSMMapOp setMapping(Mapping mapping) {
        this.mapping = mapping;
        return this;
    }

    /**
     * Sets the message to use for the commit that is created
     * 
     * @param message the commit message
     * @return {@code this}
     */
    public OSMMapOp setMessage(String message) {
        this.message = message;
        return this;
    }

    @Override
    protected RevTree _call() {

        checkNotNull(mapping);

        long staged = index().countStaged(null).count();
        long unstaged = workingTree().countUnstaged(null).count();
        Preconditions.checkState((staged == 0 && unstaged == 0),
                "You must have a clean working tree and index to perform a mapping.");

        ObjectId oldTreeId = workingTree().getTree().getId();

        Iterator<Feature> nodes;
        if (mapping.canUseNodes()) {
            nodes = getFeatures("WORK_HEAD:node");
        } else {
            nodes = Iterators.emptyIterator();
        }
        Iterator<Feature> ways;
        if (mapping.canUseWays()) {
            ways = getFeatures("WORK_HEAD:way");
        } else {
            ways = Iterators.emptyIterator();
        }
        Iterator<Feature> iterator = Iterators.concat(nodes, ways);

        if (iterator.hasNext()) {
            FeatureMapFlusher insertsByParent = new FeatureMapFlusher(workingTree());
            while (iterator.hasNext()) {
                Feature feature = iterator.next();
                List<MappedFeature> mappedFeatures = mapping.map(feature);
                if (!mappedFeatures.isEmpty()) {
                    for (MappedFeature mapped : mappedFeatures) {
                        String path = mapped.getPath();
                        insertsByParent.put(path, mapped);
                    }
                }
            }
            insertsByParent.flushAll();

            ObjectId newTreeId = workingTree().getTree().getId();
            // If the mapping generates the same mapped features that already exist, we do nothing
            if (!newTreeId.equals(oldTreeId)) {
                command(AddOp.class).call();
                command(CommitOp.class).setMessage(message).call();
                command(WriteOSMMappingEntries.class).setMapping(mapping)
                        .setMappingLogEntry(new OSMMappingLogEntry(oldTreeId, newTreeId)).call();
            }

        }

        return workingTree().getTree();

    }

    private Iterator<Feature> getFeatures(String ref) {
        Optional<ObjectId> id = command(RevParse.class).setRefSpec(ref).call();
        if (!id.isPresent()) {
            return Iterators.emptyIterator();
        }
        LsTreeOp op = command(LsTreeOp.class).setStrategy(Strategy.DEPTHFIRST_ONLY_FEATURES)
                .setReference(ref);

        Iterator<NodeRef> iterator = op.call();

        Function<NodeRef, Feature> nodeRefToFeature = new Function<NodeRef, Feature>() {

            private final Map<String, FeatureBuilder> builders = //
            ImmutableMap.<String, FeatureBuilder> of(//
                    OSMUtils.NODE_TYPE_NAME, //
                    new FeatureBuilder(RevFeatureTypeImpl.build(OSMUtils.nodeType())), //
                    OSMUtils.WAY_TYPE_NAME,//
                    new FeatureBuilder(RevFeatureTypeImpl.build(OSMUtils.wayType())));

            private final RevObjectParse parseCommand = command(RevObjectParse.class);

            @Override
            @Nullable
            public Feature apply(@Nullable NodeRef ref) {
                RevFeature revFeature = parseCommand.setObjectId(ref.getObjectId())
                        .call(RevFeature.class).get();
                final String parentPath = ref.getParentPath();
                FeatureBuilder featureBuilder = builders.get(parentPath);
                String fid = ref.name();
                Feature feature = featureBuilder.build(fid, revFeature);
                return feature;
            }

        };
        return Iterators.transform(iterator, nodeRefToFeature);
    }
}
