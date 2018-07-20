/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.shp;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.geotools.plumbing.ExportDiffOp;
import org.locationtech.geogig.geotools.plumbing.ExportOp;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.opengis.feature.Feature;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.base.Optional;

/**
 * Exports features from a feature type into a shapefile.
 *
 * @see ExportOp
 */
@Parameters(commandNames = "export-diff", commandDescription = "Export changed features to Shapefile")
public class ShpExportDiff extends AbstractShpCommand implements CLICommand {

    @Parameter(description = "<commit1> <commit2> <path> <shapefile>", arity = 4)
    public List<String> args;

    @Parameter(names = { "--overwrite", "-o" }, description = "Overwrite output files")
    public boolean overwrite;

    @Parameter(names = {
            "--old" }, description = "Export features from the old version instead of the most recent one")
    public boolean old;

    /**
     * Charset to use for encoding attributes in DBF file
     */
    @Parameter(names = {
            "--charset" }, description = "Use the specified charset to encode attributes. Default is ISO-8859-1.")
    public String charset = "ISO-8859-1";

    /**
     * Executes the export command using the provided options.
     */
    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        if (args.size() != 4) {
            printUsage(cli);
            throw new CommandFailedException();
        }

        String commitOld = args.get(0);
        String commitNew = args.get(1);
        String path = args.get(2);
        String shapefile = args.get(3);

        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

        File file = new File(shapefile);
        if (file.exists() && !overwrite) {
            throw new CommandFailedException(
                    "The selected shapefile already exists. Use -o to overwrite");
        }

        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put(ShapefileDataStoreFactory.URLP.key, file.toURI().toURL());
        params.put(ShapefileDataStoreFactory.CREATE_SPATIAL_INDEX.key, Boolean.FALSE);
        params.put(ShapefileDataStoreFactory.ENABLE_SPATIAL_INDEX.key, Boolean.FALSE);
        params.put(ShapefileDataStoreFactory.DBFCHARSET.key, charset);

        ShapefileDataStore dataStore = (ShapefileDataStore) dataStoreFactory
                .createNewDataStore(params);

        SimpleFeatureType outputFeatureType;
        try {
            String ref = old ? commitOld : commitNew;
            outputFeatureType = getFeatureType(ref, path, cli);
        } catch (GeoToolsOpException e) {
            cli.getConsole().println("No features to export.");
            return;
        }

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.add(ExportDiffOp.CHANGE_TYPE_NAME, String.class);
        for (AttributeDescriptor descriptor : outputFeatureType.getAttributeDescriptors()) {
            builder.add(descriptor);
        }
        builder.setName(outputFeatureType.getName());
        builder.setCRS(outputFeatureType.getCoordinateReferenceSystem());
        outputFeatureType = builder.buildFeatureType();

        dataStore.createSchema(outputFeatureType);

        final String typeName = dataStore.getTypeNames()[0];
        final SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        if (!(featureSource instanceof SimpleFeatureStore)) {
            throw new CommandFailedException("Could not create feature store.");
        }
        final SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

        Function<Feature, Optional<Feature>> function = getTransformingFunction(
                dataStore.getSchema());

        ExportDiffOp op = cli.getGeogig().command(ExportDiffOp.class).setFeatureStore(featureStore)
                .setPath(path).setOldRef(commitOld).setNewRef(commitNew).setUseOld(old)
                .setTransactional(false).setFeatureTypeConversionFunction(function);
        try {
            op.setProgressListener(cli.getProgressListener()).call();
        } catch (IllegalArgumentException iae) {
            throw new org.locationtech.geogig.cli.InvalidParameterException(iae.getMessage(), iae);
        } catch (GeoToolsOpException e) {
            file.delete();
            switch (e.statusCode) {
            case MIXED_FEATURE_TYPES:
                throw new CommandFailedException(
                        "Error: The selected tree contains mixed feature types.", true);
            default:
                throw new CommandFailedException("Could not export. Error:" + e.statusCode.name(),
                        e);
            }
        }
        cli.getConsole().println(path + " exported successfully to " + shapefile);

    }

    private Function<Feature, Optional<Feature>> getTransformingFunction(
            final SimpleFeatureType featureType) {

        Function<Feature, Optional<Feature>> function = (feature) -> {

            SimpleFeatureBuilder builder = new SimpleFeatureBuilder(featureType);
            for (Property property : feature.getProperties()) {
                if (property instanceof GeometryAttribute) {
                    builder.set(featureType.getGeometryDescriptor().getName(), property.getValue());
                } else {
                    builder.set(property.getName(), property.getValue());
                }
            }
            Feature modifiedFeature = builder.buildFeature(feature.getIdentifier().getID());
            return Optional.fromNullable(modifiedFeature);
        };

        return function;
    }

    private SimpleFeatureType getFeatureType(String ref, String path, GeogigCLI cli) {

        checkParameter(path != null, "No path specified.");

        final GeoGIG geogig = cli.getGeogig();

        Optional<ObjectId> rootTreeId = geogig.command(ResolveTreeish.class).setTreeish(ref).call();

        checkParameter(rootTreeId.isPresent(),
                "Couldn't resolve '" + ref + "' to a treeish object");

        RevTree rootTree = geogig.getRepository().getTree(rootTreeId.get());
        Optional<NodeRef> featureTypeTree = geogig.command(FindTreeChild.class).setChildPath(path)
                .setParent(rootTree).call();

        checkParameter(featureTypeTree.isPresent(),
                "pathspec '" + path + "' did not match any valid path");

        Optional<RevObject> revObject = cli.getGeogig().command(RevObjectParse.class)
                .setObjectId(featureTypeTree.get().getMetadataId()).call();
        if (revObject.isPresent() && revObject.get() instanceof RevFeatureType) {
            RevFeatureType revFeatureType = (RevFeatureType) revObject.get();
            if (revFeatureType.type() instanceof SimpleFeatureType) {
                return (SimpleFeatureType) revFeatureType.type();
            } else {
                throw new InvalidParameterException(
                        "Cannot find feature type for the specified path");
            }
        } else {
            throw new InvalidParameterException("Cannot find feature type for the specified path");
        }

    }
}
