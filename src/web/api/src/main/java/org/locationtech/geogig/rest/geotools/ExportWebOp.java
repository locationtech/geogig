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
import java.io.OutputStream;
import java.nio.channels.WritableByteChannel;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStore;
import org.locationtech.geogig.api.Context;
import org.locationtech.geogig.geotools.plumbing.DataStoreExportOp;
import org.locationtech.geogig.rest.TransactionalResource;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.FileRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.Variant;

import com.google.common.base.Splitter;
import com.google.common.base.Supplier;

/**
 * Base class for Geotools exports.
 */
public abstract class ExportWebOp extends TransactionalResource {

    // Form parameters
    public static final String ROOT_PARAM = "root";

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

        @Nullable
        final String rootTreeIsh = options.getFirstValue(ROOT_PARAM);

        @Nullable
        final String srcPaths = options.getValues(PATH_PARAM);
        List<String> sourceTreeNames = null;
        if (srcPaths != null) {
            sourceTreeNames = Splitter.on(',').omitEmptyStrings().splitToList(srcPaths);
        }
        // final String targetTable = options.getFirstValue(TABLE_PARAM, null);
        // if (targetTable == null) {
        // throw new CommandSpecException("\"table\" query parameter must be specified");
        // }
        // final boolean overwrite = Boolean.valueOf(options.getFirstValue(OVERWRITE_PARAM,
        // "false"));
        // final boolean alter = Boolean.valueOf(options.getFirstValue(ALTER_PARAM, "false"));
        // final boolean defaultType = Boolean.valueOf(options.getFirstValue(DEFAULT_TYPE_PARAM,
        // "false"));

        DataStoreWrapper dataStoreWrapper;
        try {
            dataStoreWrapper = getDataStoreWrapper(options);
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to obtain SimpleFeatureStore", ioe);
        }

        // if overwrite is true, remove all the features first
        // if (overwrite) {
        // try {
        // featureStore.removeFeatures(Filter.INCLUDE);
        // } catch (IOException ioe) {
        // throw new RuntimeException("Failed to trucnate table", ioe);
        // }
        // }

        // setup the Export command
        Supplier<DataStore> targetStore = dataStoreWrapper.getDataStore();
        DataStoreExportOp command = dataStoreWrapper.createCommand(context, options);
        command.setTarget(targetStore);
        command.setSourceTreePaths(sourceTreeNames);
        command.setSourceCommitish(rootTreeIsh);

        command.call();

        final MediaType mediaType = variant.getMediaType();
        final File binaryFile = dataStoreWrapper.getBinary();
        return new FileRepresentation(binaryFile, mediaType, 10) {
            @Override
            public void write(OutputStream outputStream) throws IOException {
                super.write(outputStream);
                binaryFile.delete();
            }

            @Override
            public void write(WritableByteChannel writableChannel) throws IOException {
                super.write(writableChannel);
                binaryFile.delete();
            }
        };
        //
        // AsyncContext.AsyncCommand<SimpleFeatureStore> asyncCommand;
        //
        // asyncCommand = AsyncContext.get().run(command, getCommandDescription(options));
        //
        // MediaType mediaType = variant.getMediaType();
        // return new SimpleFeatureStoreRepresentation(mediaType, asyncCommand, binaryFile);

    }

    /**
     * Create a SimpleFeatureStore from Form options.
     * 
     * @param options Form option parameters.
     * @return A wrapper object containing the target SimpleFeatureStore, target SimpleFeatureType
     *         and the ObjectID of the feature filter.
     * @throws java.io.IOException If a suitable SimpleFeatureStore can't be obtained.
     */
    public abstract DataStoreWrapper getDataStoreWrapper(Form options) throws IOException;

    public abstract String getCommandDescription(final Form options);

    public static class DataStoreWrapper {

        private Supplier<DataStore> dataStore;

        private File binary;

        public DataStoreWrapper(Supplier<DataStore> store) {
            this.dataStore = store;
        }

        public File getBinary() {
            return binary;
        }

        public void setBinary(File binary) {
            this.binary = binary;
        }

        public Supplier<DataStore> getDataStore() {
            return dataStore;
        }

        public DataStoreExportOp createCommand(final Context context, final Form options) {
            DataStoreExportOp command = context.command(DataStoreExportOp.class);
            return command;
        }
    }
}
