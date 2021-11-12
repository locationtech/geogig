/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Juan Marin (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli.geojson;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStore;
import org.geotools.data.memory.MemoryDataStore;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.geojson.feature.FeatureJSON;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.geotools.adapt.GT;
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
import org.opengis.feature.simple.SimpleFeatureType;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Exports features from a feature type into a GeoJSON file.
 * 
 * @see ExportOp
 */
@Command(name = "export", description = "Export GeoJSON")
public class GeoJsonExport extends AbstractGeoJsonCommand implements CLICommand {

    @Parameters(description = "<path> <geojson>", arity = "2")
    public List<String> args;

    @Option(names = { "--overwrite", "-o" }, description = "Overwrite output file")
    public boolean overwrite;

    @Option(names = {
            "--defaulttype" }, description = "Export only features with the tree default feature type if several types are found")
    public boolean defaultType;

    @Option(names = {
            "--alter" }, description = "Export all features if several types are found, altering them to adapt to the output feature type")
    public boolean alter;

    @Option(names = {
            "--featuretype" }, description = "Export only features with the specified feature type if several types are found")
    @Nullable
    public String sFeatureTypeId;

    /**
     * Executes the export command using the provided options.
     */
    protected @Override void runInternal(GeogigCLI cli)
            throws InvalidParameterException, CommandFailedException, IOException {

        String path = args.get(0);
        String geojson = args.get(1);

        File file = new File(geojson);
        if (file.exists() && !overwrite) {
            throw new CommandFailedException(
                    "The selected GeoJSON file already exists. Use -o to overwrite");
        }

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
            outputFeatureType = GT.adapt(cli.getGeogig().command(RevObjectParse.class)
                    .setObjectId(id.get()).call(RevFeatureType.class).get().type());
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
        DataStore dataStore = new MemoryDataStore(outputFeatureType);

        final String typeName = dataStore.getTypeNames()[0];
        final SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
        if (!(featureSource instanceof SimpleFeatureStore)) {
            throw new CommandFailedException("Could not create feature store.");
        }
        final SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        ExportOp op = cli.getGeogig().command(ExportOp.class).setFeatureStore(featureStore)
                .setPath(path).setFilterFeatureTypeId(featureTypeId).setAlter(alter);

        op.setTransactional(false);
        if (defaultType) {
            op.exportDefaultFeatureType();
        }
        FileWriter writer = null;
        try {
            op.setProgressListener(cli.getProgressListener()).call();
            FeatureJSON fjson = new FeatureJSON();
            @SuppressWarnings("rawtypes")
            FeatureCollection fc = featureSource.getFeatures();
            writer = new FileWriter(file);
            fjson.writeFeatureCollection(fc, writer);
        } catch (IllegalArgumentException iae) {
            throw new org.locationtech.geogig.cli.InvalidParameterException(iae.getMessage(), iae);
        } catch (GeoToolsOpException e) {
            file.delete();
            switch (e.statusCode) {
            case MIXED_FEATURE_TYPES:
                throw new CommandFailedException(
                        "Error: The selected tree contains mixed feature types. Use --defaulttype or --featuretype <feature_type_ref> to export.",
                        true);
            default:
                throw new CommandFailedException("Could not export. Error:" + e.statusCode.name(),
                        e);
            }
        } finally {
            writer.flush();
            writer.close();
        }
        cli.getConsole().println(path + " exported successfully to " + geojson);
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

        final Geogig geogig = cli.getGeogig();

        Optional<ObjectId> rootTreeId = geogig.command(ResolveTreeish.class)
                .setTreeish(refspec.split(":")[0]).call();

        checkParameter(rootTreeId.isPresent(),
                "Couldn't resolve '" + refspec + "' to a treeish object");

        RevTree rootTree = geogig.getRepository().context().objectDatabase()
                .getTree(rootTreeId.get());
        Optional<NodeRef> featureTypeTree = geogig.command(FindTreeChild.class)
                .setChildPath(refspec.split(":")[1]).setParent(rootTree).call();

        checkParameter(featureTypeTree.isPresent(),
                "pathspec '" + refspec.split(":")[1] + "' did not match any valid path");

        Optional<RevObject> revObject = cli.getGeogig().command(RevObjectParse.class)
                .setObjectId(featureTypeTree.get().metadataId()).call();
        if (revObject.isPresent() && revObject.get() instanceof RevFeatureType) {
            RevFeatureType revFeatureType = (RevFeatureType) revObject.get();
            return GT.adapt(revFeatureType.type());
        } else {
            throw new InvalidParameterException("Cannot find feature type for the specified path");
        }

    }

}
