/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geopkg;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

import org.geotools.data.DataStore;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.GeoPkgDataStoreFactory;
import org.locationtech.geogig.geotools.geopkg.GeopkgDataStoreExportOp;
import org.locationtech.geogig.geotools.plumbing.DataStoreExportOp;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.rest.AsyncCommandRepresentation;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.CommandRepresentationFactory;
import org.locationtech.geogig.rest.Variants;
import org.locationtech.geogig.rest.geotools.Export;
import org.locationtech.geogig.rest.geotools.Export.OutputFormat;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.ParameterSet;
import org.locationtech.geogig.web.api.RESTUtils;
import org.locationtech.geogig.web.api.StreamWriterException;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.springframework.http.MediaType;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;

/**
 * {@link OutputFormat} implementation for exporting from a repository snapshot to a geopackage
 * database file.
 * <p>
 * Additional command parameters:
 * <ul>
 * <li><b>interchange</b>: boolean indicating whether to enable GeoGig's interchange format
 * extension for GeoPackage.
 * </ul>
 * <p>
 * API usage example:
 * 
 * <pre>
 * <code>
 * GET http://localhost:8082/export?format=gpkg&interchange=true&root=HEAD&path=buildings,places
 * </code>
 * </pre>
 * <p>
 * The output of the {@link Export} command when used with the {@code format=gpkg} argument
 * will be produced by the {@link GeopgkExportRepresentation}, which in turn is created by the
 * {@link RepresentationFactory} using the {@link CommandRepresentationFactory} SPI by means of the
 * {@code META-INF/services/org.locationtech.geogig.rest.CommandRepresentationFactory} text file.
 * <p>
 * The {@link #createCommand(CommandContext)} method override returns an instance of
 * {@link GeopkgDataStoreExportOp} which decorates {@link DataStoreExportOp} by adding support to
 * enable the geopackage interchange extension on the exported layers.
 * 
 * @see Export
 * @see GeopkgDataStoreExportOp
 */
public class GeoPkgExportOutputFormat extends Export.OutputFormat {

    public static final String INTERCHANGE_PARAM = "interchange";

    protected boolean enableInterchangeExtension;

    static class TempGeoPackageSupplier implements Supplier<DataStore> {

        private File targetFile;

        private DataStore dataStore;

        public File getTargetFile() {
            if (targetFile == null) {
                try {
                    targetFile = File.createTempFile("GeoGigGeoPkgExport", ".gpkg");
                    targetFile.deleteOnExit();
                    GeoPackage gpkg = new GeoPackage(targetFile);
                    try {
                        gpkg.init();
                    } finally {
                        gpkg.close();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return targetFile;
        }

        @Override
        public DataStore get() {
            if (dataStore == null) {
                final File databaseFile = getTargetFile();

                final GeoPkgDataStoreFactory factory = new GeoPkgDataStoreFactory();

                final Map<String, Serializable> params = ImmutableMap.of(
                        GeoPkgDataStoreFactory.DBTYPE.key, "geopkg",
                        GeoPkgDataStoreFactory.DATABASE.key, databaseFile.getAbsolutePath());

                DataStore dataStore;
                try {
                    dataStore = factory.createDataStore(params);
                } catch (IOException ioe) {
                    throw new RuntimeException("Unable to create GeoPkgDataStore", ioe);
                }
                if (null == dataStore) {
                    throw new RuntimeException("Unable to create GeoPkgDataStore");
                }
                this.dataStore = dataStore;

            }
            return dataStore;
        }

    }

    protected TempGeoPackageSupplier dataStore;

    public GeoPkgExportOutputFormat(ParameterSet options) {
        setIntechangeExtension(Boolean.parseBoolean(options.getFirstValue(INTERCHANGE_PARAM)));
        this.dataStore = new TempGeoPackageSupplier();
    }

    public void setIntechangeExtension(boolean enable) {
        this.enableInterchangeExtension = enable;
    }

    @Override
    public String getCommandDescription() {
        return "Export to Geopackage database"
                + (enableInterchangeExtension ? " with geogig interchange format extension" : "");
    }

    @Override
    public DataStoreExportOp<File> createCommand(final CommandContext context) {
        boolean enableInterchangeFormat = this.enableInterchangeExtension;
        return context.getRepository().command(GeopkgDataStoreExportOp.class)
                .setInterchangeFormat(enableInterchangeFormat)
                .setDatabaseFile(dataStore.getTargetFile());
    }

    @Override
    public Supplier<DataStore> getDataStore() {
        return dataStore;
    }

    public static class RepresentationFactory implements CommandRepresentationFactory<File> {

        @Override
        public boolean supports(Class<? extends AbstractGeoGigOp<?>> cmdClass) {
            return GeopkgDataStoreExportOp.class.equals(cmdClass);
        }

        @Override
        public AsyncCommandRepresentation<File> newRepresentation(AsyncCommand<File> cmd,
                boolean cleanup) {

            return new GeopgkExportRepresentation(cmd, cleanup);
        }
    }

    public static class GeopgkExportRepresentation extends AsyncCommandRepresentation<File> {

        public GeopgkExportRepresentation(AsyncCommand<File> cmd, boolean cleanup) {
            super(cmd, cleanup);
        }

        @Override
        protected void writeResultBody(StreamingWriter w, File result) throws StreamWriterException {

            final String link = "tasks/" + super.cmd.getTaskId() + "/download";
            encodeDownloadURL(w, link);

        }

        private void encodeDownloadURL(StreamingWriter w, String link) throws StreamWriterException {

            final MediaType outputFormat = Variants.GEOPKG_MEDIA_TYPE;

            w.writeStartElement("atom:link");
            w.writeAttribute("xmlns:atom", "http://www.w3.org/2005/Atom");
            w.writeAttribute("rel", "alternate");
            w.writeAttribute("href", RESTUtils.buildHref(baseURL, link, (MediaType)null));
            w.writeAttribute("type", outputFormat.toString());
            w.writeEndElement();
        }
    }
}
