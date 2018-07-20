/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geopkg;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.geotools.data.DataStore;
import org.geotools.factory.Hints;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.geotools.cli.DataStoreExport;
import org.locationtech.geogig.geotools.geopkg.GeopkgAuditExport;
import org.locationtech.geogig.geotools.geopkg.InterchangeFormat;
import org.locationtech.geogig.geotools.plumbing.ExportOp;
import org.locationtech.geogig.repository.Repository;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;

/**
 * Exports features from a feature type into a Geopackage database.
 * 
 * @see ExportOp
 */
@RequiresRepository(true)
@ReadOnly
@Parameters(commandNames = "export", commandDescription = "Export to Geopackage")
public class GeopkgExport extends DataStoreExport implements CLICommand {
    /**
     * Common arguments for Geopackage commands.
     */
    @ParametersDelegate
    final GeopkgCommonArgs commonArgs = new GeopkgCommonArgs();

    final GeopkgSupport support = new GeopkgSupport();

    @VisibleForTesting
    @Parameter(names = { "-i",
            "--interchange" }, description = "Export as geogig mobile interchange format")
    boolean interchangeFormat;

    @Override
    protected DataStore getDataStore() {
        return support.getDataStore(commonArgs);
    }

    private final ConcurrentMap<String, String> fidMappings = new ConcurrentHashMap<String, String>();

    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        super.runInternal(cli);
        // Add mapped feature ids table to geopackage
        final String sourcePathspec = args.get(0);
        final String targetTableName = args.get(1);
        File file = new File(commonArgs.database);
        InterchangeFormat format = new InterchangeFormat(file, cli.getGeogig().getContext());

        format.createFIDMappingTable(fidMappings, targetTableName);

        if (interchangeFormat) {

            Repository repo = cli.getGeogig().getRepository();
            try {

                repo.command(GeopkgAuditExport.class).setDatabase(file)
                        .setSourcePathspec(sourcePathspec).setTargetTableName(targetTableName)
                        .setProgressListener(cli.getProgressListener()).call();

            } catch (Exception e) {
                throw new CommandFailedException("Unable to export: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Transforms all features to use a feature id that is compatible with GeoPackage. Keeps track
     * of all the mappings so they can be added to the geopackage file after the export.
     * 
     * @param featureType the feature type of the features to transform
     * @return the transforming function
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
