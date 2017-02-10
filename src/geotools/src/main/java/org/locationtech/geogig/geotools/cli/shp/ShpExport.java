/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.shp;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.geotools.plumbing.ExportOp;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeatureType;
import org.locationtech.geogig.model.RevObject;
import org.locationtech.geogig.model.RevObject.TYPE;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.plumbing.FindTreeChild;
import org.locationtech.geogig.plumbing.ResolveObjectType;
import org.locationtech.geogig.plumbing.ResolveTreeish;
import org.locationtech.geogig.plumbing.RevObjectParse;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.opengis.feature.Feature;
import org.opengis.feature.GeometryAttribute;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeatureType;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Function;
import com.google.common.base.Optional;

/**
 * Exports features from a feature type into a shapefile.
 *
 * @see ExportOp
 */
@ReadOnly
@Parameters(commandNames = "export", commandDescription = "Export to Shapefile")
public class ShpExport extends AbstractShpCommand implements CLICommand {

    @Parameter(description = "<path> <shapefile>", arity = 2)
    public List<String> args = new ArrayList<>();

    @Parameter(names = { "--overwrite", "-o" }, description = "Overwrite output file")
    public boolean overwrite;

    @Parameter(names = {
            "--defaulttype" }, description = "Export only features with the tree default feature type if several types are found")
    public boolean defaultType;

    @Parameter(names = {
            "--alter" }, description = "Export all features if several types are found, altering them to adapt to the output feature type")
    public boolean alter;

    @Parameter(names = {
            "--featuretype" }, description = "Export only features with the specified feature type if several types are found")
    @Nullable
    public String sFeatureTypeId;

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
        if (args.size() != 2) {
            printUsage(cli);
            throw new CommandFailedException();
        }

        String path = args.get(0);
        String shapefile = args.get(1);

        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

        File targetShapefile = new File(shapefile);
        if (!targetShapefile.isAbsolute()) {
            File pwd = cli.getGeogig().getPlatform().pwd();
            String relativePath = targetShapefile.getPath();
            targetShapefile = new File(pwd, relativePath);
        }
        if (targetShapefile.exists() && !overwrite) {
            throw new CommandFailedException(
                    "The selected shapefile already exists. Use -o to overwrite");
        }

        Map<String, Serializable> params = new HashMap<String, Serializable>();
        URL targetShapefileAsUrl = targetShapefile.toURI().toURL();
        params.put(ShapefileDataStoreFactory.URLP.key, targetShapefileAsUrl);
        params.put(ShapefileDataStoreFactory.CREATE_SPATIAL_INDEX.key, Boolean.FALSE);
        params.put(ShapefileDataStoreFactory.ENABLE_SPATIAL_INDEX.key, Boolean.FALSE);
        params.put(ShapefileDataStoreFactory.DBFCHARSET.key, charset);

        ShapefileDataStore dataStore = (ShapefileDataStore) dataStoreFactory
                .createNewDataStore(params);

        SimpleFeatureType outputFeatureType;
        ObjectId featureTypeId;
        if (sFeatureTypeId != null) {
            // Check the feature type id string is a correct id
            Optional<ObjectId> id = cli.getGeogig().command(RevParse.class)
                    .setRefSpec(sFeatureTypeId).call();
            checkParameter(id.isPresent(), "Invalid feature type reference", sFeatureTypeId);
            TYPE type = cli.getGeogig().command(ResolveObjectType.class).setObjectId(id.get())
                    .call();
            checkParameter(type.equals(TYPE.FEATURETYPE),
                    "Provided reference does not resolve to a feature type: ", sFeatureTypeId);
            outputFeatureType = (SimpleFeatureType) cli.getGeogig().command(RevObjectParse.class)
                    .setObjectId(id.get()).call(RevFeatureType.class).get().type();
            featureTypeId = id.get();
        } else {
            try {
                outputFeatureType = getFeatureType(path, cli);
                featureTypeId = null;
            } catch (GeoToolsOpException e) {
                cli.getConsole().println("No features to export.");
                return;
            }
        }
        dataStore.createSchema(outputFeatureType);

        final String typeName = dataStore.getTypeNames()[0];
        final SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        if (!(featureSource instanceof SimpleFeatureStore)) {
            throw new CommandFailedException("Could not create feature store.");
        }

        Function<Feature, Optional<Feature>> function = getTransformingFunction(
                dataStore.getSchema());

        final SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        ExportOp op = cli.getGeogig().command(ExportOp.class).setFeatureStore(featureStore)
                .setPath(path).setFilterFeatureTypeId(featureTypeId).setAlter(alter)
                .setFeatureTypeConversionFunction(function);
        // shapefile transactions are memory bound, so avoid them
        op.setTransactional(false);
        if (defaultType) {
            op.exportDefaultFeatureType();
        }
        try {
            op.setProgressListener(cli.getProgressListener()).call();
        } catch (IllegalArgumentException iae) {
            throw new org.locationtech.geogig.cli.InvalidParameterException(iae.getMessage(), iae);
        } catch (GeoToolsOpException e) {
            targetShapefile.delete();
            switch (e.statusCode) {
            case MIXED_FEATURE_TYPES:
                throw new CommandFailedException(
                        "Error: The selected tree contains mixed feature types. Use --defaulttype or --featuretype <feature_type_ref> to export.",
                        true);
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
                    String name = property.getName().getLocalPart();
                    if (name.length() > 10) {
                        name = name.substring(0, 10);
                    }
                    builder.set(name, property.getValue());
                }
            }
            Feature modifiedFeature = builder.buildFeature(feature.getIdentifier().getID());
            return Optional.fromNullable(modifiedFeature);
        };
        return function;
    }

    private SimpleFeatureType getFeatureType(String path, GeogigCLI cli) {

        checkParameter(path != null, "No path specified.");

        String refspec;
        if (path.contains(":")) {
            refspec = path;
        } else {
            refspec = "WORK_HEAD:" + path;
        }

        checkParameter(!refspec.endsWith(":"), "No path specified.");

        final GeoGIG geogig = cli.getGeogig();

        Optional<ObjectId> rootTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish(refspec.split(":")[0]).call();

        checkParameter(rootTreeId.isPresent(),
                "Couldn't resolve '" + refspec + "' to a treeish object");

        RevTree rootTree = geogig.getRepository().getTree(rootTreeId.get());
        Optional<NodeRef> featureTypeTree = geogig.command(FindTreeChild.class)
                .setChildPath(refspec.split(":")[1]).setParent(rootTree).call();

        checkParameter(featureTypeTree.isPresent(),
                "pathspec '" + refspec.split(":")[1] + "' did not match any valid path");

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
