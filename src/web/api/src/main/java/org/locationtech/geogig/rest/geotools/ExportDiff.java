/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rest.geotools;

import java.io.IOException;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.geotools.data.DataStore;
import org.locationtech.geogig.geotools.plumbing.DataStoreExportOp;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.Representations;
import org.locationtech.geogig.rest.geopkg.GeoPkgExportDiffOutputFormat;
import org.locationtech.geogig.rest.geotools.Export.OutputFormat;
import org.locationtech.geogig.web.api.AbstractWebAPICommand;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.ParameterSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;

/**
 * Command for exporting the diff between two commits in a geotools format.
 * <p>
 * Concrete format options are handled by specializations of {@link OutputFormat}.
 * <p>
 * Arguments:
 * <ul>
 * <li><b>format</b>: Mandatory, output format for the exported data. Currently only {@code GPKG} is
 * supported. Format argument value is case insensitive.
 * <li><b>oldRef</b>: Mandatory, older commit to use in diff comparison.
 * <li><b>newRef</b>: Mandatory, newer commit to use in diff comparison.
 * <li><b>path</b>: Optional, a comma separated list of layer names to export. Defaults to exporting
 * the diffs of all layers in the resolved root tree.
 * </ul>
 * <p>
 * <b>NOTE</b>: export-diff format specializations may add additional format specific arguments.
 * 
 * <p>
 * Usage: {@code GET <repository url>/export-diff[.xml|.json]?format=
 * <format name>&oldRef=<old ref>&newRef=<new ref>[&path=[layerName]+]}
 * 
 * @see DataStoreExportOp
 * @see GeoPkgExportDiffOutputFormat
 */
public class ExportDiff extends AbstractWebAPICommand {

    // Form parameters
    public static final String FORMAT_PARAM = "format";

    public static final String OLD_REF_PARAM = "oldRef";

    public static final String NEW_REF_PARAM = "newRef";

    public static final String PATH_PARAM = "path";

    private String format, oldRef, newRef, path;

    @VisibleForTesting
    public ParameterSet options;

    private OutputFormat outputFormat;

    @VisibleForTesting
    public AsyncContext asyncContext;

    @Override
    protected void setParametersInternal(ParameterSet options) {
        this.options = options;
        setOutputFormat(options.getFirstValue(FORMAT_PARAM));
        setOldRef(options.getFirstValue(OLD_REF_PARAM));
        setNewRef(options.getFirstValue(NEW_REF_PARAM));
        setPath(options.getFirstValue(PATH_PARAM));
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

    public void setOldRef(@Nullable String oldRef) {
        this.oldRef = oldRef;
    }

    public void setNewRef(@Nullable String newRef) {
        this.newRef = newRef;
    }

    public void setPath(@Nullable String paths) {
        this.path = paths;
    }

    OutputFormat getDataStoreWrapper(final String format, final ParameterSet options)
            throws IOException {

        if ("gpkg".equalsIgnoreCase(format)) {
            return new GeoPkgExportDiffOutputFormat(oldRef, newRef, options);
        }

        throw new IllegalArgumentException("Unsupported output format: " + format);
    }

    @Override
    protected void runInternal(CommandContext context) {
        final OutputFormat outputFormat = resolveOutputFormat();

        @Nullable
        final List<String> sourceTreeNames = parseTreePahts(this.path);

        // setup the Export command
        Supplier<DataStore> targetStore = outputFormat.getDataStore();
        DataStoreExportOp<?> command = outputFormat.createCommand(context);
        command.setTarget(targetStore);
        command.setSourceTreePaths(sourceTreeNames);

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

}
