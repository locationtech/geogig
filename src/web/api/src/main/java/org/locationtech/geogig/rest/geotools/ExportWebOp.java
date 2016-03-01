/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geotools;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureStore;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.api.ObjectId;
import org.locationtech.geogig.geotools.plumbing.ExportOp;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.TransactionalResource;
import org.opengis.filter.Filter;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.Variant;

/**
 * Base class for Geotools exports.
 */
public abstract class ExportWebOp extends TransactionalResource {

    // Form parameters
    public static final String PATH_PARAM = "path";
    public static final String TABLE_PARAM = "table";
    public static final String OVERWRITE_PARAM = "overwrite";
    public static final String ALTER_PARAM = "alter";
    public static final String DEFAULT_TYPE_PARAM = "defaultType";
    public static final String FEATURE_TYPE_PARAM = "featureType";

    @Override
    public void init(org.restlet.Context context, Request request, Response response) {
        super.init(context, request, response);
        addSupportedVariants(getVariants());
    }

    protected abstract void addSupportedVariants(List<Variant> variants);

    @Override
    public Representation getRepresentation(final Variant variant) {
        final Request request = getRequest();
        final Context context = super.getContext(request);

        Form options = getRequest().getResourceRef().getQueryAsForm();

        final String srcPath = options.getFirstValue(PATH_PARAM, null);
        if (srcPath == null) {
            throw new RuntimeException("\"path\" query parameter must be specified");
        }
        final String targetTable= options.getFirstValue(TABLE_PARAM, null);
        if (targetTable == null) {
            throw new RuntimeException("\"table\" query parameter must be specified");
        }
        final boolean overwrite = Boolean.valueOf(options.getFirstValue(OVERWRITE_PARAM, "false"));
        final boolean alter = Boolean.valueOf(options.getFirstValue(ALTER_PARAM, "false"));
        final boolean defaultType = Boolean
            .valueOf(options.getFirstValue(DEFAULT_TYPE_PARAM, "false"));

        FeatureStoreWrapper featureStoreWrapper;
        try {
            featureStoreWrapper = getFeatureStoreWrapper(srcPath, targetTable,options);
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to obtain SimpleFeatureStore", ioe);
        }

        final SimpleFeatureStore featureStore = featureStoreWrapper.getFeatureStore();
        final ObjectId featureTypeFilterId = featureStoreWrapper.getFeatureTypeFilterId();
        final File binaryFile = featureStoreWrapper.getBinary();

        // if overwrite is true, remove all the features first
        if (overwrite) {
            try {
                featureStore.removeFeatures(Filter.INCLUDE);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to trucnate table", ioe);
            }
        }

        // setup the Export command
        ExportOp command = context.command(ExportOp.class);
        if (defaultType) {
            command.exportDefaultFeatureType();
        }
        command.setFeatureStore(featureStore).setAlter(alter).setPath(srcPath)
            .setFilterFeatureTypeId(featureTypeFilterId);

        AsyncContext.AsyncCommand<SimpleFeatureStore> asyncCommand;

        asyncCommand = AsyncContext.get().run(command, getCommandDescription(options));

        MediaType mediaType = variant.getMediaType();
        return new SimpleFeatureStoreRepresentation(mediaType, asyncCommand, binaryFile);

    }

    /**
     * Create a SimpleFeatureStore from Form options.
     *
     * @param srcPath Path describing which feature to export.
     * @param targetTable Table name of the destination feature table.
     * @param options Form option parameters.
     * @return A wrapper object containing the target SimpleFeatureStore, target SimpleFeatureType
     * and the ObjectID of the feature filter.
     * @throws java.io.IOException If a suitable SimpleFeatureStore can't be obtained.
     */
    public abstract FeatureStoreWrapper getFeatureStoreWrapper(String srcPath, String targetTable,
        Form options) throws IOException;

    public abstract String getCommandDescription(final Form options);

    public static class FeatureStoreWrapper {

        private SimpleFeatureStore featureStore;
        private ObjectId featureTypeFilterId;
        private File binary;

        public FeatureStoreWrapper() {
            super();
        }

        public SimpleFeatureStore getFeatureStore() {
            return featureStore;
        }

        public ObjectId getFeatureTypeFilterId() {
            return featureTypeFilterId;
        }

        public File getBinary() {
            return binary;
        }

        public void setFeatureStore(SimpleFeatureStore featureStore) {
            this.featureStore = featureStore;
        }

        public void setFeatureTypeFilterId(ObjectId featureTypeFilterId) {
            this.featureTypeFilterId = featureTypeFilterId;
        }

        public void setBinary(File binary) {
            this.binary = binary;
        }

    }
}
