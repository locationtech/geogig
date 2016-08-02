/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rest.geopkg;

import java.io.File;

import org.locationtech.geogig.geotools.geopkg.GeopkgDataStoreExportDiffOp;
import org.locationtech.geogig.geotools.plumbing.DataStoreExportOp;
import org.locationtech.geogig.rest.geotools.Export.OutputFormat;
import org.locationtech.geogig.web.api.CommandContext;
import org.locationtech.geogig.web.api.ParameterSet;

/**
 * {@link OutputFormat} implementation for exporting the changes between two commits to a geopackage
 * database file.
 * <p>
 * API usage example:
 * 
 * <pre>
 * <code>
 * GET http://localhost:8082/export-diff?format=gpkg&oldRef=1ab3fc2a&newRef=master
 * </code>
 * </pre>
 * <p>
 * 
 * @see GeoPkgExportOutputFormat
 * @see GeopkgDataStoreExportDiffOp
 */
public class GeoPkgExportDiffOutputFormat extends GeoPkgExportOutputFormat {

    private final String oldRef;

    private final String newRef;

    public GeoPkgExportDiffOutputFormat(String oldRef, String newRef, ParameterSet options) {
        super(options);
        this.oldRef = oldRef;
        this.newRef = newRef;
    }

    @Override
    public String getCommandDescription() {
        return "Export changes between two commits to Geopackage database";
    }

    @Override
    public DataStoreExportOp<File> createCommand(final CommandContext context) {
        return context.getRepository().command(GeopkgDataStoreExportDiffOp.class)
                .setDatabaseFile(dataStore.getTargetFile()).setOldRef(oldRef).setNewRef(newRef);
    }

}
