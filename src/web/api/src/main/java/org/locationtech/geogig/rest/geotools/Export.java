/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 * Gabriel Roldan (Boundless) - pull up functionality to new internal command DataStoreExportOp
 */
package org.locationtech.geogig.rest.geotools;

import static org.locationtech.geogig.repository.impl.SpatialOps.parseBBOX;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStore;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.geogig.geotools.plumbing.DataStoreExportOp;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.Representations;
import org.locationtech.geogig.rest.geopkg.GeoPkgExportOutputFormat;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.ParameterSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;

/**
 * Command for Geotools exports through the WEB API.
 * <p>
 * Concrete format options are handled by specializations of {@link OutputFormat}.
 * <p>
 * Arguments:
 * <ul>
 * <li><b>format</b>: Mandatory, output format for the exported data. Currently only {@code GPKG} is
 * supported. Format argument value is case insensitive.
 * <li><b>root</b>: Optional, defaults to {@code HEAD}. ref spec that resolves to the root tree from
 * where to export data (e.g. {@code HEAD}, {@code WORK_HEAD}, a branch name like {@code master},
 * {@code refs/heads/master}, a commit id, possibly abbreviated like {@code 50e295dd}, a relative
 * refspec like {@code HEAD~2}, {@code master^4}, etc)
 * <li><b>path</b>: Optional, a comma separated list of layer names to export. Defaults to exporting
 * all layers in the resolved root tree.
 * <li><b>bbox</b>: Optional, a bounding box filter. If present, only features matching the
 * indicated bounding box filter will be exported. Applies to all exported layers. Format is
 * {@code minx,miny,maxx,maxy,<SRS>}, where SRS is the EPSG code for the coordinates (e.g.
 * {@code EPSG:4326}, {@code EPSG:26986}, etc), always using <b>"longitude first"</b> axis order.
 * </ul>
 * <p>
 * <b>NOTE</b>: export format specializations may add additional format specific arguments.
 * 
 * <p>
 * Usage:
 * {@code GET <repository url>/export[.xml|.json]?format=<format name>[&root=<refspec>][&path=[layerName]+][&bbox=<minx,miny,maxx,maxy,SRS>]}
 * <p>
 * Usage example:
 * 
 * <pre>
 * <code>
 * GET <repository url>/export?format=GPKG
 * GET <repository url>/export?format=GPKG&root=HEAD~1&path=buildings,roads,places
 * GET <repository url>/export?format=GPKG&root=HEAD~1&path=buildings,roads,places&bbox=-180,-90,0,90,EPSG:4326
 * </code>
 * </pre>
 * 
 * @see DataStoreExportOp
 * @see GeoPkgExportOutputFormat
 */
public class Export extends AbstractWebAPICommand {

    // Form parameters
    public static final String FORMAT_PARAM = "format";

    public static final String ROOT_PARAM = "root";

    public static final String PATH_PARAM = "path";

    public static final String BBOX_PARAM = "bbox";

    private String format, root, path, bbox;

    @VisibleForTesting
    public ParameterSet options;

    private OutputFormat outputFormat;

    @VisibleForTesting
    public AsyncContext asyncContext;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        this.options = options;
        setOutputFormat(options.getFirstValue(FORMAT_PARAM));
        setRoot(options.getFirstValue(ROOT_PARAM));
        setPath(options.getFirstValue(PATH_PARAM));
        setBBox(options.getFirstValue(BBOX_PARAM));
    }

    @Override
    public boolean requiresTransaction() {
        return false;
    }

    /**
     * @param format Mandatory, format identifier for the export output.
     */
    public void setOutputFormat(String format) {
        this.format = format;
        this.outputFormat = null;
    }

    @VisibleForTesting
    void setOutputFormat(OutputFormat format) {
        this.outputFormat = format;
        this.format = null;
    }

    public void setRoot(@Nullable String refSpec) {
        this.root = refSpec;
    }

    public void setPath(@Nullable String paths) {
        this.path = paths;
    }

    public void setBBox(@Nullable String bbox) {
        this.bbox = bbox;
    }

    /**
     * Create a SimpleFeatureStore from Form options.
     * 
     * @return A wrapper object containing the target SimpleFeatureStore, target SimpleFeatureType
     *         and the ObjectID of the feature filter.
     * @throws java.io.IOException If a suitable SimpleFeatureStore can't be obtained.
     */
    OutputFormat getDataStoreWrapper(final String format, final ParameterSet options)
            throws IOException {

        if ("gpkg".equalsIgnoreCase(format)) {
            return new GeoPkgExportOutputFormat(options);
        }

        throw new IllegalArgumentException("Unsupported output format: " + format);
    }

    @Override
    protected void runInternal(CommandContext context) {
        final OutputFormat outputFormat = resolveOutputFormat();

        @Nullable
        final String rootTreeIsh = this.root;

        @Nullable
        final ReferencedEnvelope bboxFilter = parseBBOX(this.bbox);

        @Nullable
        final List<String> sourceTreeNames = parseTreePahts(this.path);

        // setup the Export command
        Supplier<DataStore> targetStore = outputFormat.getDataStore();
        DataStoreExportOp<?> command = outputFormat.createCommand(context);
        command.setTarget(targetStore);
        command.setSourceTreePaths(sourceTreeNames);
        command.setSourceCommitish(rootTreeIsh);
        command.setBBoxFilter(bboxFilter);

        final String commandDescription = outputFormat.getCommandDescription();

        AsyncContext asyncContext = this.asyncContext;
        if (asyncContext == null) {
            asyncContext = AsyncContext.get();
        }
        final AsyncCommand<?> asyncCommand = asyncContext.run(command, commandDescription);

        context.setResponseContent(Representations.newRepresentation(asyncCommand, false));
    }

    private OutputFormat resolveOutputFormat() {
        final String format = this.format;
        OutputFormat outputFormat = this.outputFormat;
        Preconditions.checkArgument(format != null || outputFormat != null,
                "output format not provided");
        if (format != null) {
            try {
                outputFormat = getDataStoreWrapper(format, options);
            } catch (IOException ioe) {
                throw new RuntimeException("Failed to obtain target DataStore", ioe);
            }
        }
        return outputFormat;
    }

    @Nullable
    private List<String> parseTreePahts(@Nullable String srcPaths) {
        List<String> sourceTreeNames = null;
        if (srcPaths != null) {
            sourceTreeNames = Splitter.on(',').omitEmptyStrings().splitToList(srcPaths);
        }
        return sourceTreeNames;
    }

    public static abstract class OutputFormat {

        private File binary;

        public abstract String getCommandDescription();

        public abstract Supplier<DataStore> getDataStore();

        public File getBinary() {
            return binary;
        }

        public void setBinary(File binary) {
            this.binary = binary;
        }

        public abstract DataStoreExportOp<?> createCommand(final CommandContext context);
    }

}
