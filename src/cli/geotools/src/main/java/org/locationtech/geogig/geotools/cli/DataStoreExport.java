/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStore;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureTypeImpl;
import org.locationtech.geogig.cli.AbstractCommand;
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
import org.locationtech.geogig.repository.Repository;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

import com.beust.jcommander.Parameter;
import com.google.common.base.Function;
import com.google.common.base.Optional;

/**
 * Exports features from a geogig feature type into a {@link DataStore} given by the concrete
 * subclass.
 * 
 * @see ExportOp
 */
@ReadOnly
public abstract class DataStoreExport extends AbstractCommand implements CLICommand {

    @Parameter(description = "[<commit-ish>:]<path> <table> (define source feature type tree and target table name)", arity = 2)
    public List<String> args = new ArrayList<>();

    @Parameter(names = { "--overwrite", "-o" }, description = "Overwrite output table")
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

    protected abstract DataStore getDataStore();

    /**
     * Executes the export command using the provided options.
     */
    @Override
    protected void runInternal(GeogigCLI cli) throws IOException {
        if (args.size() != 2) {
            printUsage(cli);
            throw new CommandFailedException();
        }

        final String sourceTreeIsh = args.get(0);
        final String targetTableName = args.get(1);

        checkParameter(targetTableName != null && !targetTableName.isEmpty(),
                "No table name specified");

        DataStore dataStore = getDataStore();
        try {
            exportInternal(cli, sourceTreeIsh, targetTableName, dataStore);
        } finally {
            dataStore.dispose();
        }
    }

    private void exportInternal(final GeogigCLI cli, final String sourceTreeIsh,
            final String tableName, final DataStore dataStore) throws IOException {

        ObjectId featureTypeId = null;
        SimpleFeatureType outputFeatureType = null;
        if (!Arrays.asList(dataStore.getTypeNames()).contains(tableName)) {
            if (sFeatureTypeId != null) {
                // Check the feature type id string is a correct id
                Optional<ObjectId> id = cli.getGeogig().command(RevParse.class)
                        .setRefSpec(sFeatureTypeId).call();
                checkParameter(id.isPresent(), "Invalid feature type reference", sFeatureTypeId);
                TYPE type = cli.getGeogig().command(ResolveObjectType.class).setObjectId(id.get())
                        .call();
                checkParameter(type.equals(TYPE.FEATURETYPE),
                        "Provided reference does not resolve to a feature type: ", sFeatureTypeId);
                outputFeatureType = (SimpleFeatureType) cli.getGeogig()
                        .command(RevObjectParse.class).setObjectId(id.get())
                        .call(RevFeatureType.class).get().type();
                featureTypeId = id.get();
            } else {
                outputFeatureType = getFeatureType(cli, sourceTreeIsh, tableName);
            }
            try {
                dataStore.createSchema(outputFeatureType);
            } catch (IOException e) {
                throw new CommandFailedException("Cannot create new table in database", e);
            }
        } else {
            if (!overwrite) {
                throw new CommandFailedException(
                        "The selected table already exists. Use -o to overwrite");
            } else {
                outputFeatureType = getFeatureType(cli, sourceTreeIsh, tableName);
            }
        }

        SimpleFeatureSource featureSource = dataStore.getFeatureSource(tableName);
        if (!(featureSource instanceof SimpleFeatureStore)) {
            throw new CommandFailedException("Can't write to the selected table");
        }
        SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
        if (overwrite) {
            try {
                featureStore.removeFeatures(Filter.INCLUDE);
            } catch (IOException e) {
                throw new CommandFailedException("Error truncating table: " + e.getMessage(), e);
            }
        }

        ExportOp op = cli.getGeogig().command(ExportOp.class).setFeatureStore(featureStore)
                .setPath(sourceTreeIsh).setFilterFeatureTypeId(featureTypeId).setAlter(alter);
        if (defaultType) {
            op.exportDefaultFeatureType();
        }

        Function<Feature, Optional<Feature>> transformingFunction = getTransformingFunction(
                outputFeatureType);
        if (transformingFunction != null) {
            op.setFeatureTypeConversionFunction(transformingFunction);
        }

        try {
            op.setProgressListener(cli.getProgressListener()).call();
        } catch (IllegalArgumentException iae) {
            throw new org.locationtech.geogig.cli.InvalidParameterException(iae.getMessage(), iae);
        } catch (GeoToolsOpException e) {
            switch (e.statusCode) {
            case MIXED_FEATURE_TYPES:
                throw new CommandFailedException(
                        "The selected tree contains mixed feature types. Use --defaulttype or --featuretype <feature_type_ref> to export.",
                        true);
            default:
                throw new CommandFailedException("Could not export. Error:" + e.statusCode.name(),
                        e);
            }
        }

        cli.getConsole().println(sourceTreeIsh + " exported successfully to " + tableName);
    }

    /**
     * Returns a transforming function that will be run against all features to be exported. The
     * function may return {@code Optional.absent()}, which prevents that particular feature from
     * being exported.
     * 
     * @param featureType the feature type of the features to transform
     * @return the transforming function
     */
    protected Function<Feature, Optional<Feature>> getTransformingFunction(
            final SimpleFeatureType featureType) {
        return null;
    }

    private SimpleFeatureType getFeatureType(final GeogigCLI cli, final String sourceTreeIsh,
            final String tableName) {
        final Repository repository = cli.getGeogig().getRepository();

        checkParameter(sourceTreeIsh != null, "No path specified.");

        final String headTreeish;
        final String featureTreePath;
        final Optional<ObjectId> rootTreeId;

        {
            String refspec;
            if (sourceTreeIsh.contains(":")) {
                refspec = sourceTreeIsh;
            } else {
                refspec = "WORK_HEAD:" + sourceTreeIsh;
            }

            checkParameter(!refspec.endsWith(":"), "No path specified.");

            String[] split = refspec.split(":");
            headTreeish = split[0];
            featureTreePath = split[1];
            rootTreeId = repository.command(ResolveTreeish.class).setTreeish(headTreeish).call();

            checkParameter(rootTreeId.isPresent(),
                    "Couldn't resolve '" + refspec + "' to a treeish object");

        }

        final RevTree rootTree = repository.getTree(rootTreeId.get());
        Optional<NodeRef> featureTypeTree = repository.command(FindTreeChild.class)
                .setChildPath(featureTreePath).setParent(rootTree).call();

        checkParameter(featureTypeTree.isPresent(),
                "pathspec '" + featureTreePath + "' did not match any valid path");

        Optional<RevObject> revObject = repository.command(RevObjectParse.class)
                .setObjectId(featureTypeTree.get().getMetadataId()).call();
        if (revObject.isPresent() && revObject.get() instanceof RevFeatureType) {
            RevFeatureType revFeatureType = (RevFeatureType) revObject.get();
            if (revFeatureType.type() instanceof SimpleFeatureType) {
                SimpleFeatureType sft = (SimpleFeatureType) revFeatureType.type();
                return new SimpleFeatureTypeImpl(new NameImpl(tableName),
                        sft.getAttributeDescriptors(), sft.getGeometryDescriptor(),
                        sft.isAbstract(), sft.getRestrictions(), sft.getSuper(),
                        sft.getDescription());
            } else {
                throw new InvalidParameterException(
                        "Cannot find feature type for the specified path");
            }
        } else {
            throw new InvalidParameterException("Cannot find feature type for the specified path");
        }

    }

}
