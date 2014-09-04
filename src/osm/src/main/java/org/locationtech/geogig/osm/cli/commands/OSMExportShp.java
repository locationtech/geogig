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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.geotools.cli.porcelain.AbstractShpCommand;
import org.locationtech.geogig.geotools.plumbing.ExportOp;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException;
import org.locationtech.geogig.osm.internal.MappedFeature;
import org.locationtech.geogig.osm.internal.Mapping;
import org.locationtech.geogig.osm.internal.MappingRule;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.vividsolutions.jts.geom.Point;

/**
 * Exports OSM into a shapefile, using a data mapping
 * 
 * @see ExportOp
 */
@ReadOnly
@Parameters(commandNames = "export-shp", commandDescription = "Export OSM data to shapefile, using a data mapping")
public class OSMExportShp extends AbstractShpCommand implements CLICommand {

    @Parameter(description = "<shapefile>", arity = 2)
    public List<String> args;

    @Parameter(names = { "--overwrite", "-o" }, description = "Overwrite output file")
    public boolean overwrite;

    @Parameter(names = { "--mapping" }, description = "The file that contains the data mapping to use")
    public String mappingFile;

    /**
     * Executes the export command using the provided options.
     */
    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        Preconditions.checkNotNull(mappingFile != null, "A data mapping file must be specified");

        if (args == null || args.isEmpty() || args.size() != 1) {
            printUsage(cli);
            throw new CommandFailedException();
        }

        String shapefile = args.get(0);

        File file = new File(shapefile);
        if (file.exists() && !overwrite) {
            throw new CommandFailedException(
                    "The selected shapefile already exists. Use -o to overwrite");
        }

        final Mapping mapping = Mapping.fromFile(mappingFile);
        List<MappingRule> rules = mapping.getRules();
        checkParameter(!rules.isEmpty(), "No rules are defined in the specified mapping");
        Function<Feature, Optional<Feature>> function = new Function<Feature, Optional<Feature>>() {

            @Override
            @Nullable
            public Optional<Feature> apply(@Nullable Feature feature) {
                Optional<MappedFeature> mapped = mapping.map(feature);
                if (mapped.isPresent()) {
                    return Optional.of(mapped.get().getFeature());
                }
                return Optional.absent();
            }

        };

        SimpleFeatureType outputFeatureType = mapping.getRules().get(0).getFeatureType();
        String path = getOriginTreesFromOutputFeatureType(outputFeatureType);

        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put(ShapefileDataStoreFactory.URLP.key, new File(shapefile).toURI().toURL());
        params.put(ShapefileDataStoreFactory.CREATE_SPATIAL_INDEX.key, Boolean.TRUE);
        ShapefileDataStore dataStore = (ShapefileDataStore) dataStoreFactory
                .createNewDataStore(params);
        dataStore.createSchema(outputFeatureType);

        final String typeName = dataStore.getTypeNames()[0];
        final SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        if (!(featureSource instanceof SimpleFeatureStore)) {
            throw new CommandFailedException(
                    "Could not create feature store. Shapefile may be read only");
        }
        final SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        ExportOp op = cli.getGeogig().command(ExportOp.class).setFeatureStore(featureStore)
                .setPath(path).setFeatureTypeConversionFunction(function);
        try {
            op.setProgressListener(cli.getProgressListener()).call();
            cli.getConsole().println("OSM data exported successfully to " + shapefile);
        } catch (IllegalArgumentException iae) {
            file.delete();
            throw new org.locationtech.geogig.cli.InvalidParameterException(iae.getMessage(), iae);
        } catch (GeoToolsOpException e) {
            file.delete();
            throw new CommandFailedException("Could not export. Error:" + e.statusCode.name(), e);
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
