/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.geotools.geopkg;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.geotools.data.DataStore;
import org.geotools.factory.Hints;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.geotools.plumbing.DataStoreExportOp;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.porcelain.DiffOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * Exports changes between two commits to a geopackage file. The features that were changed between
 * the two commits are written to a table in the geopackage, and the change types are logged in a
 * change table.
 */
public class GeopkgDataStoreExportDiffOp extends DataStoreExportOp<File> {

    private File geopackage;

    private final ConcurrentMap<String, String> fidMappings = new ConcurrentHashMap<String, String>();

    private final AtomicLong nextId = new AtomicLong(1);

    private String oldRef = null;

    private Map<String, ChangeType> changedNodes = new HashMap<String, ChangeType>();

    /**
     * @param oldRef the old version to compare against
     * @return {@code this}
     */
    public GeopkgDataStoreExportDiffOp setOldRef(String oldRef) {
        this.oldRef = oldRef;
        return this;
    }

    /**
     * @param newRef the new version to compare against
     * @return {@code this}
     */
    public GeopkgDataStoreExportDiffOp setNewRef(String newRef) {
        this.setSourceCommitish(newRef);
        return this;
    }

    /**
     * @param geopackage the geopackage database file
     * @return {@code this}
     */
    public GeopkgDataStoreExportDiffOp setDatabaseFile(File geopackage) {
        this.geopackage = geopackage;
        return this;
    }

    @Override
    protected void export(final String refSpec, final DataStore targetStore,
            final String targetTableName, final ProgressListener progress) {
        Preconditions.checkArgument(oldRef != null, "Old ref not specified.");
        Preconditions.checkArgument(getSourceCommitish() != null, "New ref not specified.");

        changedNodes.clear();

        InterchangeFormat format = new InterchangeFormat(geopackage, context());

        try (final AutoCloseableIterator<DiffEntry> diff = context.command(DiffOp.class)
                .setOldVersion(oldRef).setNewVersion(getSourceCommitish())
                .setFilter(targetTableName).call()) {
            while (diff.hasNext()) {
                DiffEntry entry = diff.next();
                changedNodes.put(entry.newName() != null ? entry.newName() : entry.oldName(),
                        entry.changeType());
            }
        }

        super.export(refSpec, targetStore, targetTableName, progress);

        try {
            format.createFIDMappingTable(fidMappings, targetTableName);
            // create change log
            format.createChangeLog(targetTableName, changedNodes);
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected File buildResult(DataStore targetStore) {
        return geopackage;
    }

    /**
     * @param featureType the feature type of the features to transform
     * @return a transform function to update and keep track of feature id mappings of exported
     *         features
     */
    @Override
    protected Function<Feature, Optional<Feature>> getTransformingFunction(
            final SimpleFeatureType featureType) {
        Function<Feature, Optional<Feature>> function = (feature) -> {
            // Return optional.absent for features that were not part of the diff
            String featureId = feature.getIdentifier().getID();
            if (!changedNodes.containsKey(featureId)) {
                return Optional.absent();
            }

            SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);
            builder.init((SimpleFeature) feature);
            long fidValue = nextId.incrementAndGet();
            builder.featureUserData(Hints.PROVIDED_FID, Long.valueOf(fidValue));
            fidMappings.put(Long.toString(fidValue), featureId);
            Feature modifiedFeature = builder.buildFeature(Long.toString(fidValue));
            return Optional.fromNullable(modifiedFeature);
        };

        return function;
    }
}
