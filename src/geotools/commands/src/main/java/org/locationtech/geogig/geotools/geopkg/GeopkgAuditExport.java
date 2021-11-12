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

import static org.locationtech.geogig.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

import java.io.File;
import java.io.IOException;

import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.impl.AbstractGeoGigOp;

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

    protected @Override Void _call() {
        requireNonNull(databaseFile, "GeoPackage file not provided");
        checkState(databaseFile.exists(), "GeoPackage file %s does not exist", databaseFile);

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
