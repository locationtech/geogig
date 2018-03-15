/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.geopkg;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.geotools.data.DataStore;
import org.geotools.factory.Hints;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.geotools.plumbing.DataStoreExportOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.base.Function;
import com.google.common.base.Optional;

/**
 * Exports layers from a repository snapshot to a GeoPackage file.
 * <p>
 * Enabling the GeoGig geopackage interchange format extension is enabled through the
 * {@link #setInterchangeFormat(boolean) interchangeFormat} argument.
 * <p>
 * Implementation detail: since the GeoTools geopackage datastore does not expose the file it writes
 * to, it shall be given as an argument through {@link #setDatabaseFile(File)}, while the
 * {@link DataStore} given at {@link #setDataStore} must already be a geopackage one.
 * 
 * @see DataStoreExportOp
 * @see GeopkgAuditExport
 */
public class GeopkgDataStoreExportOp extends DataStoreExportOp<File> {

    private boolean enableInterchangeFormat;

    private File geopackage;

    private final ConcurrentMap<String, String> fidMappings = new ConcurrentHashMap<String, String>();

    private final AtomicLong nextId = new AtomicLong(1);

    public GeopkgDataStoreExportOp setInterchangeFormat(boolean enable) {
        this.enableInterchangeFormat = enable;
        return this;
    }

    public GeopkgDataStoreExportOp setDatabaseFile(File geopackage) {
        this.geopackage = geopackage;
        return this;
    }

    /**
     * Overrides to call {@code super.export} and then enable the geopackage interchange format
     * after the data has been exported for the given layer. {@inheritDoc}
     */
    @Override
    protected void export(final String refSpec, final DataStore targetStore,
            final String targetTableName, final ProgressListener progress) {

        super.export(refSpec, targetStore, targetTableName, progress);

        InterchangeFormat format = new InterchangeFormat(geopackage, context());

        try {
            format.createFIDMappingTable(fidMappings, targetTableName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (enableInterchangeFormat) {
            command(GeopkgAuditExport.class).setSourcePathspec(refSpec)
                    .setTargetTableName(targetTableName).setDatabase(geopackage).call();
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

            SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);
            builder.init((SimpleFeature) feature);
            long fidValue = nextId.incrementAndGet();
            builder.featureUserData(Hints.PROVIDED_FID, Long.valueOf(fidValue));
            fidMappings.put(Long.toString(fidValue), feature.getIdentifier().getID());
            Feature modifiedFeature = builder.buildFeature(Long.toString(fidValue));
            return Optional.fromNullable(modifiedFeature);
        };

        return function;
    }
}
