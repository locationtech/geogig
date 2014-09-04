/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.cli.commands;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.geotools.cli.porcelain.AbstractPGCommand;
import org.locationtech.geogig.geotools.plumbing.ExportOp;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException;
import org.locationtech.geogig.osm.internal.Mapping;
import org.locationtech.geogig.osm.internal.MappingRule;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.Filter;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Point;

/**
 * Exports OSM into a PostGIS database, using a data mapping
 * 
 * @see ExportOp
 */
@ReadOnly
@Parameters(commandNames = "export-pg", commandDescription = "Export OSM data to a PostGIS database, using a data mapping")
public class OSMExportPG extends AbstractPGCommand implements CLICommand {

    @Parameter(names = { "--overwrite", "-o" }, description = "Overwrite output tables")
    public boolean overwrite;

    @Parameter(names = { "--mapping" }, description = "The file that contains the data mapping to use")
    public String mappingFile;

    /**
     * Executes the export command using the provided options.
     */
    @Override
    protected void runInternal(GeogigCLI cli) {
        Preconditions.checkNotNull(mappingFile != null, "A data mapping file must be specified");

        final Mapping mapping = Mapping.fromFile(mappingFile);
        List<MappingRule> rules = mapping.getRules();
        checkParameter(!rules.isEmpty(), "No rules are defined in the specified mapping");

        for (final MappingRule rule : mapping.getRules()) {
            Function<Feature, Optional<Feature>> function = new Function<Feature, Optional<Feature>>() {

                @Override
                @Nullable
                public Optional<Feature> apply(@Nullable Feature feature) {
                    Optional<Feature> mapped = rule.apply(feature);
                    if (mapped.isPresent()) {
                        return Optional.of(mapped.get());
                    }
                    return Optional.absent();
                }

            };
            SimpleFeatureType outputFeatureType = rule.getFeatureType();
            String path = getOriginTreesFromOutputFeatureType(outputFeatureType);
            DataStore dataStore = null;
            try {
                dataStore = getDataStore();

                String tableName = outputFeatureType.getName().getLocalPart();
                if (Arrays.asList(dataStore.getTypeNames()).contains(tableName)) {
                    if (!overwrite) {
                        throw new CommandFailedException("A table named '" + tableName
                                + "'already exists. Use -o to overwrite");
                    }
                } else {
                    try {
                        dataStore.createSchema(outputFeatureType);
                    } catch (IOException e) {
                        throw new CommandFailedException("Cannot create new table in database", e);
                    }
                }

                final SimpleFeatureSource featureSource = dataStore.getFeatureSource(tableName);
                if (!(featureSource instanceof SimpleFeatureStore)) {
                    throw new CommandFailedException(
                            "Could not create feature store. Data source is read only.");
                }
                final SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
                if (overwrite) {
                    featureStore.removeFeatures(Filter.INCLUDE);
                }
                ExportOp op = cli.getGeogig().command(ExportOp.class).setFeatureStore(featureStore)
                        .setPath(path).setFeatureTypeConversionFunction(function);
                try {
                    op.setProgressListener(cli.getProgressListener()).call();
                    cli.getConsole().println("OSM data exported successfully to " + tableName);
                } catch (IllegalArgumentException iae) {
                    throw new org.locationtech.geogig.cli.InvalidParameterException(iae.getMessage(), iae);
                } catch (GeoToolsOpException e) {
                    throw new CommandFailedException("Could not export. Error:"
                            + e.statusCode.name(), e);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot connect to database: " + e.getMessage(), e);
            } finally {
                if (dataStore != null) {
                    dataStore.dispose();
                }
            }
        }
    }

    private String getOriginTreesFromOutputFeatureType(SimpleFeatureType featureType) {
        GeometryDescriptor descriptor = featureType.getGeometryDescriptor();
        Class<?> clazz = descriptor.getType().getBinding();
        if (clazz.equals(Point.class)) {
            return "node";
        } else {
            return "way";
        }
    }
}
