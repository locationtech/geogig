/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.geotools.geopkg;

import java.io.File;

import org.locationtech.geogig.geotools.plumbing.DataStoreImportOp;

/**
 * Imports layers from a GeoPackage file to the repository.
 * 
 * @see DataStoreImportOp
 * @see GeopkgAuditImport
 */
public class GeopkgDataStoreAuditImportOp extends DataStoreImportOp<GeopkgImportResult> {

    private File geopackage;

    public GeopkgDataStoreAuditImportOp setDatabaseFile(File geopackage) {
        this.geopackage = geopackage;
        return this;
    }

    @Override
    protected GeopkgImportResult callInternal() {
        GeopkgImportResult auditResult;
        try {
            auditResult = command(GeopkgAuditImport.class).setDatabase(geopackage)
                    .setAuthorName(authorName).setAuthorEmail(authorEmail)
                    .setCommitMessage(commitMessage).setTable(table).call();

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        }
        return auditResult;
    }
}
