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

import java.util.Iterator;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevFeature;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry;
import org.locationtech.geogig.api.plumbing.diff.DiffEntry.ChangeType;
import org.locationtech.geogig.api.porcelain.DiffOp;
import org.locationtech.geogig.di.CanRunDuringConflict;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.PropertyDescriptor;
import org.openstreetmap.osmosis.core.container.v0_6.ChangeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.task.common.ChangeAction;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;

//import org.locationtech.geogig.api.plumbing.diff.DiffEntry.ChangeType;

/**
 * Perform a diff between trees pointed out by two commits, and return the result as OSM changesets
 * 
 * @see CreateOSMChangesetOp
 */
@CanRunDuringConflict
public class CreateOSMChangesetOp extends AbstractGeoGigOp<Iterator<ChangeContainer>> {

    private String oldRefSpec;

    private String newRefSpec;

    private Long id;

    /**
     * @param revObjectSpec the old version to compare against
     * @return {@code this}
     */
    public CreateOSMChangesetOp setOldVersion(@Nullable String revObjectSpec) {
        this.oldRefSpec = revObjectSpec;
        return this;
    }

    /**
     * @param treeishOid the old {@link ObjectId} to compare against
     * @return {@code this}
     */
    public CreateOSMChangesetOp setOldVersion(ObjectId treeishOid) {
        return setOldVersion(treeishOid.toString());
    }

    /**
     * @param revObjectSpec the new version to compare against
     * @return {@code this}
     */
    public CreateOSMChangesetOp setNewVersion(String revObjectSpec) {
        this.newRefSpec = revObjectSpec;
        return this;
    }

    /**
     * @param treeishOid the new {@link ObjectId} to compare against
     * @return {@code this}
     */
    public CreateOSMChangesetOp setNewVersion(ObjectId treeishOid) {
        return setNewVersion(treeishOid.toString());
    }

    /**
     * Sets the Id to be used to replace negative IDs. This is to be used if creating a changeset
     * for a dataset that contains modified entities. These entities do not have an id assigned, so
     * the OSM API should be queried to obtain the changeset id, and then used here to replace the
     * temporary negative IDs that are used as placeholders
     * 
     * @param id the id used to replace negative ids
     */
    public CreateOSMChangesetOp setId(Long id) {
        this.id = id;
        return this;

    }

    /**
     * Executes the diff operation.
     * 
     * @return an iterator to a set of differences between the two trees
     * @see DiffEntry
     */
    @Override
    protected Iterator<ChangeContainer> _call() {

        Iterator<DiffEntry> nodeIterator = command(DiffOp.class).setFilter(OSMUtils.NODE_TYPE_NAME)
                .setNewVersion(newRefSpec).setOldVersion(oldRefSpec).setReportTrees(false).call();
        Iterator<DiffEntry> wayIterator = command(DiffOp.class).setFilter(OSMUtils.WAY_TYPE_NAME)
                .setNewVersion(newRefSpec).setOldVersion(oldRefSpec).setReportTrees(false).call();
        Iterator<DiffEntry> iterator = Iterators.concat(nodeIterator, wayIterator);

        final EntityConverter converter = new EntityConverter();
        Function<DiffEntry, ChangeContainer> function = new Function<DiffEntry, ChangeContainer>() {

            @Override
            @Nullable
            public ChangeContainer apply(@Nullable DiffEntry diff) {
                NodeRef ref = diff.changeType().equals(ChangeType.REMOVED) ? diff.getOldObject()
                        : diff.getNewObject();
                RevFeature revFeature = command(RevObjectParse.class).setObjectId(ref.objectId())
                        .call(RevFeature.class).get();
                RevFeatureType revFeatureType = command(RevObjectParse.class)
                        .setObjectId(ref.getMetadataId()).call(RevFeatureType.class).get();
                SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(
                        (SimpleFeatureType) revFeatureType.type());
                ImmutableList<PropertyDescriptor> descriptors = revFeatureType.sortedDescriptors();
                ImmutableList<Optional<Object>> values = revFeature.getValues();
                for (int i = 0; i < descriptors.size(); i++) {
                    PropertyDescriptor descriptor = descriptors.get(i);
                    Optional<Object> value = values.get(i);
                    featureBuilder.set(descriptor.getName(), value.orNull());
                }
                SimpleFeature feature = featureBuilder.buildFeature(ref.name());
                Entity entity = converter.toEntity(feature, id);
                EntityContainer container;
                if (entity instanceof Node) {
                    container = new NodeContainer((Node) entity);
                } else {
                    container = new WayContainer((Way) entity);
                }

                ChangeAction action = diff.changeType().equals(ChangeType.ADDED) ? ChangeAction.Create
                        : diff.changeType().equals(ChangeType.MODIFIED) ? ChangeAction.Modify
                                : ChangeAction.Delete;

                return new ChangeContainer(container, action);

            }

        };
        return Iterators.transform(iterator, function);
    }

}
