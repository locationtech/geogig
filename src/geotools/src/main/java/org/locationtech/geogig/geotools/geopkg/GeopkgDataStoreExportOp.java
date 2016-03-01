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

public class GeopkgDataStoreExportOp extends DataStoreExportOp {

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

    @Override
    protected void export(final String treeSpec, final DataStore targetStore,
            final String targetTableName, final ProgressListener progress) {

        super.export(treeSpec, targetStore, targetTableName, progress);

        if (enableInterchangeFormat) {
            command(GeopkgAuditExport.class).setSourceTreeish(treeSpec)
                    .setTargetTableName(targetTableName).setDatabase(geopackage).call();
        }
    }
}
