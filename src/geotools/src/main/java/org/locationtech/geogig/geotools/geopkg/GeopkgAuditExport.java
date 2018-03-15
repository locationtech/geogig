/* Copyright (c) 2015-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.geotools.geopkg;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.io.IOException;

import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;

public class GeopkgAuditExport extends AbstractGeoGigOp<Void> {

    private File databaseFile;

    private String sourcePathspec;

    private String targetTableName;

    public GeopkgAuditExport setDatabase(File file) {
        this.databaseFile = file;
        return this;
    }

    public GeopkgAuditExport setSourcePathspec(String sourcePathspec) {
        this.sourcePathspec = sourcePathspec;
        return this;
    }

    public GeopkgAuditExport setTargetTableName(String targetTableName) {
        this.targetTableName = targetTableName;
        return this;
    }

    @Override
    protected Void _call() {
        checkNotNull(databaseFile, "GeoPackage file not provided");
        checkState(databaseFile.exists(), "GeoPackage file %s does not exist", databaseFile);

        Repository repository = repository();
        ProgressListener progress = getProgressListener();

        InterchangeFormat interchange;
        interchange = new InterchangeFormat(databaseFile, context()).setProgressListener(progress);

        try {
            interchange.export(sourcePathspec, targetTableName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

}
