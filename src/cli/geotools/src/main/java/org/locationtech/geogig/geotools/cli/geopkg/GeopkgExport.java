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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.geotools.data.DataStore;
import org.geotools.feature.ValidatingFeatureFactoryImpl;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.util.factory.Hints;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.geotools.cli.base.DataStoreExport;
import org.locationtech.geogig.geotools.geopkg.GeopkgAuditExport;
import org.locationtech.geogig.geotools.geopkg.InterchangeFormat;
import org.locationtech.geogig.geotools.plumbing.ExportOp;
import org.locationtech.geogig.repository.Repository;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.google.common.annotations.VisibleForTesting;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Exports features from a feature type into a Geopackage database.
 * 
 * @see ExportOp
 */
@RequiresRepository(true)
@ReadOnly
@Command(name = "export", description = "Export to Geopackage")
public class GeopkgExport extends DataStoreExport implements CLICommand {

    public @ParentCommand GeopkgCommandProxy commonArgs;

    final GeopkgSupport support = new GeopkgSupport();

    @VisibleForTesting
    @Option(names = { "-i",
            "--interchange" }, description = "Export as geogig mobile interchange format")
    boolean interchangeFormat;

    protected @Override DataStore getDataStore() {
        return support.getDataStore(commonArgs);
    }

    private final ConcurrentMap<String, String> fidMappings = new ConcurrentHashMap<String, String>();

    private final AtomicLong nextId = new AtomicLong(1);

    protected @Override void runInternal(GeogigCLI cli) throws IOException {
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
    protected @Override Function<Feature, Optional<Feature>> getTransformingFunction(
            final SimpleFeatureType featureType) {
        Function<Feature, Optional<Feature>> function = (feature) -> {
            SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType,
                    new ValidatingFeatureFactoryImpl());
            builder.init((SimpleFeature) feature);
            long fidValue = nextId.incrementAndGet();
            builder.featureUserData(Hints.PROVIDED_FID, Long.valueOf(fidValue));
            fidMappings.put(Long.toString(fidValue), feature.getIdentifier().getID());
            Feature modifiedFeature = builder.buildFeature(Long.toString(fidValue));
            return Optional.ofNullable(modifiedFeature);
        };

        return function;
    }
}
