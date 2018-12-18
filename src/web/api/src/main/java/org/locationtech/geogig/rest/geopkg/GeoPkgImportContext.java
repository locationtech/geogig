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

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map.Entry;

import org.geotools.data.DataStore;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.geotools.jdbc.JDBCDataStore;
import org.locationtech.geogig.geotools.geopkg.GeopkgDataStoreAuditImportOp;
import org.locationtech.geogig.geotools.geopkg.GeopkgDataStoreImportOp;
import org.locationtech.geogig.geotools.geopkg.GeopkgImportResult;
import org.locationtech.geogig.geotools.geopkg.GeopkgMergeConflictsException;
import org.locationtech.geogig.geotools.geopkg.RocksdbMap;
import org.locationtech.geogig.geotools.plumbing.DataStoreImportOp;
import org.locationtech.geogig.geotools.plumbing.DataStoreImportOp.DataStoreSupplier;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.plumbing.FindCommonAncestor;
import org.locationtech.geogig.plumbing.merge.MergeScenarioReport;
import org.locationtech.geogig.plumbing.merge.ReportMergeScenarioOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.rest.AsyncCommandRepresentation;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.CommandRepresentationFactory;
import org.locationtech.geogig.rest.geotools.DataStoreImportContextService;
import org.locationtech.geogig.spring.controller.RepositoryCommandController;
import org.locationtech.geogig.storage.AutoCloseableIterator;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.PagedMergeScenarioConsumer;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.locationtech.geogig.web.api.StreamWriterException;
import org.locationtech.geogig.web.api.StreamingWriter;

import com.google.common.base.Optional;

/**
 * Geopackage specific implementation of {@link DataStoreImportContextService}.
 */
public class GeoPkgImportContext implements DataStoreImportContextService {

    private static final String SUPPORTED_FORMAT = "gpkg";

    private DataStoreSupplier dataStoreSupplier;

    @Override
    public boolean accepts(String format) {
        return SUPPORTED_FORMAT.equals(format);
    }

    @Override
    public String getCommandDescription() {
        return "Importing GeoPackage database file.";
    }

    @Override
    public DataStoreSupplier getDataStore(ParameterSet options) {
        if (dataStoreSupplier == null) {
            dataStoreSupplier = new GpkgDataStoreSupplier(options);
        }
        return dataStoreSupplier;
    }

    @Override
    public DataStoreImportOp<?> createCommand(final Context context, final ParameterSet options) {
        if (Boolean.parseBoolean(options.getFirstValue("interchange", "false"))) {
            return context.command(GeopkgDataStoreAuditImportOp.class)
                    .setDatabaseFile(options.getUploadedFile());
        }
        return context.command(GeopkgDataStoreImportOp.class)
                .setDatabaseFile(options.getUploadedFile());
    }

    private static class GpkgDataStoreSupplier implements DataStoreSupplier {

        private JDBCDataStore dataStore;

        private final ParameterSet options;

        private final File uploadedFile;

        GpkgDataStoreSupplier(ParameterSet options) {
            super();
            this.options = options;
            this.uploadedFile = options.getUploadedFile();
        }

        @Override
        public DataStore get() {
            if (null == dataStore) {
                // build one
                createDataStore();
            }
            return dataStore;
        }

        private void createDataStore() {
            final GeoPkgDataStoreFactory factory = new GeoPkgDataStoreFactory();
            final HashMap<String, Serializable> params = new HashMap<>(3);
            if (uploadedFile == null) {
                throw new CommandSpecException("Request must specify one and only one "
                        + RepositoryCommandController.UPLOAD_FILE_KEY + " in the request body");
            }
            // fill in DataStore parameters
            params.put(GeoPkgDataStoreFactory.DBTYPE.key, "geopkg");
            params.put(GeoPkgDataStoreFactory.DATABASE.key, uploadedFile.getAbsolutePath());
            params.put(GeoPkgDataStoreFactory.USER.key, options.getFirstValue("user", "user"));
            try {
                dataStore = factory.createDataStore(params);
            } catch (IOException ioe) {
                throw new CommandSpecException(
                        "Unable to create GeoPkgDataStore: " + ioe.getMessage());
            }
            if (null == dataStore) {
                throw new CommandSpecException(
                        "Unable to create GeoPkgDataStore from uploaded file.");
            }
        }

        @Override
        public void cleanupResources() {
            dataStore = null;
            if (uploadedFile != null) {
                uploadedFile.delete();
            }
        }
    }

    public static class RepresentationFactory
            implements CommandRepresentationFactory<GeopkgImportResult> {

        @Override
        public boolean supports(Class<? extends AbstractGeoGigOp<?>> cmdClass) {
            return GeopkgDataStoreAuditImportOp.class.equals(cmdClass);
        }

        @Override
        public AsyncCommandRepresentation<GeopkgImportResult> newRepresentation(
                AsyncCommand<GeopkgImportResult> cmd, boolean cleanup) {

            return new GeopkgAuditImportRepresentation(cmd, cleanup);
        }
    }

    public static class GeopkgAuditImportRepresentation
            extends AsyncCommandRepresentation<GeopkgImportResult> {

        public GeopkgAuditImportRepresentation(AsyncCommand<GeopkgImportResult> cmd,
                boolean cleanup) {
            super(cmd, cleanup);
        }

        /**
         * @throws StreamWriterException
         */
        protected @Override void writeResultBody(StreamingWriter w, GeopkgImportResult result) {
            ResponseWriter out = new ResponseWriter(w, getMediaType());
            writeImportResult(result, w, out);
        }

        /**
         * @throws StreamWriterException
         */
        protected @Override void writeError(StreamingWriter w, Throwable cause) {
            if (cause instanceof GeopkgMergeConflictsException) {
                Context context = cmd.getContext();
                GeopkgMergeConflictsException m = (GeopkgMergeConflictsException) cause;
                final RevCommit ours = context.repository().getCommit(m.getOurs());
                final RevCommit theirs = context.repository().getCommit(m.getTheirs());
                final Optional<ObjectId> ancestor = context.command(FindCommonAncestor.class)
                        .setLeft(ours).setRight(theirs).call();
                PagedMergeScenarioConsumer consumer = new PagedMergeScenarioConsumer(0);
                final MergeScenarioReport report = context.command(ReportMergeScenarioOp.class)
                        .setMergeIntoCommit(ours).setToMergeCommit(theirs).setConsumer(consumer)
                        .call();
                ResponseWriter out = new ResponseWriter(w, getMediaType());
                Optional<RevCommit> mergeCommit = Optional.absent();
                w.writeStartElement("result");
                out.writeMergeConflictsResponse(mergeCommit, report, context, ours.getId(),
                        theirs.getId(), ancestor.get(), consumer);
                w.writeStartElement("import");
                writeImportResult(m.importResult, w, out);
                w.writeEndElement();
                w.writeEndElement();
            } else {
                super.writeError(w, cause);
            }
        }

        /**
         * @throws StreamWriterException
         */
        private void writeImportResult(GeopkgImportResult result, StreamingWriter w,
                ResponseWriter out) {
            if (result.getNewCommit() != null) {
                out.writeCommit(result.getNewCommit(), "newCommit", null, null, null);
            }
            out.writeCommit(result.getImportCommit(), "importCommit", null, null, null);
            w.writeStartElement("NewFeatures");
            w.writeStartArray("type");
            for (Entry<String, RocksdbMap> layerMappings : result.getNewMappings().entrySet()) {
                w.writeStartArrayElement("type");
                w.writeAttribute("name", layerMappings.getKey());
                w.writeStartArray("id");
                try (AutoCloseableIterator<Entry<String, String>> mappingIterator = layerMappings
                        .getValue().entryIterator()) {
                    while (mappingIterator.hasNext()) {
                        Entry<String, String> mapping = mappingIterator.next();
                        w.writeStartArrayElement("id");
                        w.writeAttribute("provided", mapping.getKey());
                        w.writeAttribute("assigned", mapping.getValue());
                        w.writeEndArrayElement();
                    }
                }
                w.writeEndArray();
                w.writeEndArrayElement();
            }
            w.writeEndArray();
            w.writeEndElement();
        }
    }
}
