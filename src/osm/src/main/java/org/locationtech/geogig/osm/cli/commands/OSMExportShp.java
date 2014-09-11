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

import static com.google.common.io.Files.getNameWithoutExtension;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.geotools.data.DataStore;
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

        final File file = new File(shapefile);

        final Mapping mapping = Mapping.fromFile(mappingFile);
        List<MappingRule> rules = mapping.getRules();
        checkParameter(!rules.isEmpty(), "No rules are defined in the specified mapping");

        for (MappingRule rule : rules) {
            File targetFile = file;
            if (rules.size() > 1) {
                String name = getNameWithoutExtension(file.getName()) + "_" + rule.getName()
                        + ".shp";
                targetFile = new File(file.getParentFile(), name);
            }
            exportRule(rule, cli, targetFile);
        }
    }

    private void exportRule(final MappingRule rule, final GeogigCLI cli, final File shapeFile)
            throws IOException {
        if (shapeFile.exists() && !overwrite) {
            throw new CommandFailedException(
                    "The selected shapefile already exists. Use -o to overwrite");
        }

        Function<Feature, Optional<Feature>> function = new Function<Feature, Optional<Feature>>() {

            @Override
            @Nullable
            public Optional<Feature> apply(@Nullable Feature feature) {
                Optional<Feature> mapped = rule.apply(feature);
                return mapped;
            }
        };

        SimpleFeatureType outputFeatureType = rule.getFeatureType();
        String path = getOriginTreesFromOutputFeatureType(outputFeatureType);

        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put(ShapefileDataStoreFactory.URLP.key, shapeFile.toURI().toURL());
        params.put(ShapefileDataStoreFactory.CREATE_SPATIAL_INDEX.key, Boolean.TRUE);

        DataStore dataStore = dataStoreFactory.createNewDataStore(params);
        try {
            dataStore.createSchema(outputFeatureType);

            final String typeName = dataStore.getTypeNames()[0];
            final SimpleFeatureSource source = dataStore.getFeatureSource(typeName);
            checkParameter(source instanceof SimpleFeatureStore,
                    "Could not create feature store. Shapefile may be read only");
            final SimpleFeatureStore store = (SimpleFeatureStore) source;
            ExportOp op = cli.getGeogig().command(ExportOp.class).setFeatureStore(store)
                    .setPath(path).setFeatureTypeConversionFunction(function);
            try {
                op.setProgressListener(cli.getProgressListener()).call();
                cli.getConsole().println("OSM data exported successfully to " + shapeFile);
            } catch (IllegalArgumentException iae) {
                shapeFile.delete();
                throw new org.locationtech.geogig.cli.InvalidParameterException(iae.getMessage(),
                        iae);
            } catch (GeoToolsOpException e) {
                shapeFile.delete();
                throw new CommandFailedException("Could not export. Error:" + e.statusCode.name(),
                        e);
            }
        } finally {
            dataStore.dispose();
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
