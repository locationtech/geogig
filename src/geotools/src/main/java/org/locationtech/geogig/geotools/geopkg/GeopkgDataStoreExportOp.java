/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.geopkg;

import java.io.File;

import org.geotools.data.DataStore;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.geotools.plumbing.DataStoreExportOp;

/**
 * Exports layers from a repository snapshot to a GeoPackage file.
 * <p>
 * Enabling the GeoGig geopackage interchange format extension is enabled through the
 * {@link #setInterchangeFormat(boolean) interchangeFormat} argument.
 * <p>
 * Implementation detail: since the GeoTools geopackage datastore does not expose the file it writes
 * to, it shall be given as an argument through {@link #setDatabaseFile(File)}, while the
 * {@link DataStore} given at {@link #setDataStore(DataStore)} must already be a geopackage one.
 * 
 * @see DataStoreExportOp
 * @see GeopkgAuditExport
 */
public class GeopkgDataStoreExportOp extends DataStoreExportOp<File> {

    private boolean enableInterchangeFormat;

    private File geopackage;

    public GeopkgDataStoreExportOp setInterchangeFormat(boolean enable) {
        this.enableInterchangeFormat = enable;
        return this;
    }

    public GeopkgDataStoreExportOp setDatabaseFile(File geopackage) {
        this.geopackage = geopackage;
        return this;
    }

    /**
     * Overrides to call {@code super.export} and then enable the geopackage interchange format
     * after the data has been exported for the given layer. {@inheritDoc}
     */
    @Override
    protected void export(final String treeSpec, final DataStore targetStore,
            final String targetTableName, final ProgressListener progress) {

        super.export(treeSpec, targetStore, targetTableName, progress);

        if (enableInterchangeFormat) {
            command(GeopkgAuditExport.class).setSourceTreeish(treeSpec)
                    .setTargetTableName(targetTableName).setDatabase(geopackage).call();
        }
    }

    @Override
    protected File buildResult(DataStore targetStore) {
        return geopackage;
    }
}
