/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geopkg;

import static org.locationtech.geogig.cli.AbstractCommand.checkParameter;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureTypeImpl;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.NodeRef;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.api.RevFeatureType;
import org.locationtech.geogig.api.RevObject;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.api.plumbing.FindTreeChild;
import org.locationtech.geogig.api.plumbing.ResolveObjectType;
import org.locationtech.geogig.api.plumbing.ResolveTreeish;
import org.locationtech.geogig.api.plumbing.RevObjectParse;
import org.locationtech.geogig.api.plumbing.RevParse;
import org.locationtech.geogig.geotools.geopkg.GeopkgAuditExport;
import org.locationtech.geogig.geotools.plumbing.GeoToolsOpException;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.rest.Variants;
import org.locationtech.geogig.rest.geotools.ExportWebOp;
import org.opengis.feature.simple.SimpleFeatureType;
import org.restlet.data.Form;
import org.restlet.data.Request;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;

/**
 * Export Web Op for GeoPackage.
 */
public class GeoPkgExportWebOp extends ExportWebOp {

    public static final String INTERCHANGE_PARAM = "interchange";

    @Override
    public FeatureStoreWrapper getFeatureStoreWrapper(String srcPath, String targetTable,
        Form options) throws IOException {
        FeatureStoreWrapper wrapper = new FeatureStoreWrapper();
        // get a datastore
        final GeoPkgDataStoreFactory factory = new GeoPkgDataStoreFactory();
        final HashMap<String, Serializable> params = new HashMap<>(3);
        // fill in DataStore parameters
        params.put(GeoPkgDataStoreFactory.DBTYPE.key, "geopkg");
        // generate a temp file for the output database
        final Path databasePath = Files.createTempFile(UUID.randomUUID().toString(), ".gpkg");
        final File databaseFile = databasePath.toFile();
        params.put(GeoPkgDataStoreFactory.DATABASE.key,databaseFile.getAbsolutePath());
        params.put(GeoPkgDataStoreFactory.USER.key,
            options.getFirstValue("user", "user"));
        JDBCDataStore dataStore;
        try {
            dataStore = factory.createDataStore(params);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create GeoPkgDataStore", ioe);
        }
        if (null == dataStore) {
            throw new RuntimeException("Unable to create GeoPkgDataStore");
        }
        wrapper.setBinary(databaseFile);
        // create the Feature schema if it doesn't exist
        ObjectId featureTypeId = createFeatureTypeSchema(dataStore, srcPath, targetTable, options);
        // set the featureType filter on the wrapper
        wrapper.setFeatureTypeFilterId(featureTypeId);
        // set the SimpleFeatureStore on the wrapper
        SimpleFeatureSource featureSource = dataStore.getFeatureSource(targetTable);
        if (!SimpleFeatureStore.class.isAssignableFrom(featureSource.getClass())) {
            // not a SimpleFeatureStore
            throw new RuntimeException("Can't write to selected table. Not a SimpleFeatureStore.");
        }
        SimpleFeatureStore featureStore = SimpleFeatureStore.class.cast(featureSource);
        wrapper.setFeatureStore(featureStore);

        // handle GeoPackage interchange format
        if (Boolean.valueOf(options.getFirstValue(INTERCHANGE_PARAM, Boolean.FALSE.toString()))) {
            // need the geogig context
            final Request request = getRequest();
            final Context context = super.getContext(request);
            GeopkgAuditExport auditExport = context.command(GeopkgAuditExport.class);
            // get the DB file
            auditExport.setDatabase(databaseFile).setSourceTreeish(srcPath)
                .setTargetTableName(targetTable).call();
        }
        return wrapper;
    }

    @Override
    public String getCommandDescription(Form options) {
        return "Description coming, check back soon!";
    }

    private ObjectId createFeatureTypeSchema(JDBCDataStore dataStore, String srcPath,
        String targetTable, Form options) throws IOException {

        ObjectId featureTypeId = null;
        // see if the dataStore already has the target table
        if (!Arrays.asList(dataStore.getTypeNames()).contains(targetTable)) {
            // table doesn't exist, let's create it.
            // need the geogig context
            final Request request = getRequest();
            final Context context = super.getContext(request);
            // get any specified featureType filter
            String sFeatureTypeId = options.getFirstValue(FEATURE_TYPE_PARAM, null);
            SimpleFeatureType outputFeatureType;
            if (sFeatureTypeId != null) {
                // Check the feature type id string is a correct id
                Optional<ObjectId> id = context.command(RevParse.class)
                    .setRefSpec(sFeatureTypeId).call();
                checkParameter(id.isPresent(), "Invalid feature type reference", sFeatureTypeId);
                RevObject.TYPE type = context.command(ResolveObjectType.class).setObjectId(id.get())
                    .call();
                checkParameter(type.equals(RevObject.TYPE.FEATURETYPE),
                    "Provided reference does not resolve to a feature type: ", sFeatureTypeId);
                outputFeatureType = (SimpleFeatureType) context
                    .command(RevObjectParse.class).setObjectId(id.get())
                    .call(RevFeatureType.class).get().type();
                featureTypeId = id.get();
            } else {
                // no specified featureType, get it from the source
                try {
                    final Repository repository = context.repository();
                    SimpleFeatureType sft = getFeatureType(srcPath, repository);
                    outputFeatureType = new SimpleFeatureTypeImpl(new NameImpl(targetTable),
                        sft.getAttributeDescriptors(), sft.getGeometryDescriptor(),
                        sft.isAbstract(), sft.getRestrictions(), sft.getSuper(),
                        sft.getDescription());
                } catch (GeoToolsOpException e) {
                    throw new RuntimeException("No features to export.", e);
                }
            }
            try {
                dataStore.createSchema(outputFeatureType);
            } catch (IOException e) {
                throw new RuntimeException("Cannot create new table in database", e);
            }
        }
        return featureTypeId;
    }

    private SimpleFeatureType getFeatureType(final String sourceTreeIsh, final Repository repository) {

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

            checkParameter(rootTreeId.isPresent(), "Couldn't resolve '" + refspec
                + "' to a treeish object");

        }

        final RevTree rootTree = repository.getTree(rootTreeId.get());
        Optional<NodeRef> featureTypeTree = repository.command(FindTreeChild.class)
            .setChildPath(featureTreePath).setParent(rootTree).call();

        checkParameter(featureTypeTree.isPresent(), "pathspec '" + featureTreePath
            + "' did not match any valid path");

        Optional<RevObject> revObject = repository.command(RevObjectParse.class)
            .setObjectId(featureTypeTree.get().getMetadataId()).call();
        if (revObject.isPresent() && revObject.get() instanceof RevFeatureType) {
            RevFeatureType revFeatureType = (RevFeatureType) revObject.get();
            if (revFeatureType.type() instanceof SimpleFeatureType) {
                return (SimpleFeatureType) revFeatureType.type();
            } else {
                throw new RuntimeException(
                    "Cannot find feature type for the specified path");
            }
        } else {
            throw new RuntimeException("Cannot find feature type for the specified path");
        }

    }

    @Override
    protected void addSupportedVariants(List<Variant> variants) {
        variants.add(Variants.GEOPKG);
    }
}
