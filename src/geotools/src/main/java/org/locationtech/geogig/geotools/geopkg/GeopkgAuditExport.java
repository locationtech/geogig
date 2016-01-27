/* Copyright (c) 2015 Boundless and others.
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

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.repository.Repository;

import com.google.common.base.Throwables;

public class GeopkgAuditExport extends AbstractGeoGigOp<Void> {

    private File databaseFile;

    private String sourceTreeIsh;

    private String targetTableName;

    public GeopkgAuditExport setDatabase(File file) {
        this.databaseFile = file;
        return this;
    }

    public GeopkgAuditExport setSourceTreeish(String sourceTreeIsh) {
        this.sourceTreeIsh = sourceTreeIsh;
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
        interchange = new InterchangeFormat(databaseFile, repository).setProgressListener(progress);

        try {
            interchange.export(sourceTreeIsh, targetTableName);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        return null;
    }

}
