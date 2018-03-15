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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;

import org.locationtech.geogig.porcelain.MergeConflictsException;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.repository.ProgressListener;

public class GeopkgAuditImport extends AbstractGeoGigOp<GeopkgImportResult> {

    private String commitMessage;

    private String authorName = null;

    private String authorEmail = null;

    private File geopackageFile;

    private String table = null;

    public GeopkgAuditImport setDatabase(File geopackageFile) {
        this.geopackageFile = geopackageFile;
        return this;
    }

    public GeopkgAuditImport setCommitMessage(String message) {
        this.commitMessage = message;
        return this;
    }

    public GeopkgAuditImport setTable(String table) {
        this.table = table;
        return this;
    }

    public GeopkgAuditImport setAuthorName(String authorName) {
        this.authorName = authorName;
        return this;
    }

    public GeopkgAuditImport setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
        return this;
    }

    @Override
    protected GeopkgImportResult _call() throws IllegalArgumentException, IllegalStateException {
        checkArgument(null != geopackageFile, "Geopackage database not provided");
        checkArgument(geopackageFile.exists(), "Database %s does not exist", geopackageFile);
        checkArgument(commitMessage != null, "Commit message not provided");
        checkState(workingTree().isClean(),
                "The working tree has unstaged changes. It must be clean for the import to run cleanly.");
        checkState(stagingArea().isClean(),
                "The staging ares has uncommitted changes. It must be clean for the import to run cleanly.");

        GeopkgImportResult importResult = null;

        try {
            InterchangeFormat interchange;
            ProgressListener progress = getProgressListener();
            interchange = new InterchangeFormat(geopackageFile, context())
                    .setProgressListener(progress);

            if (table == null) {
                importResult = interchange.importAuditLog(commitMessage, authorName, authorEmail);
            } else {
                importResult = interchange.importAuditLog(commitMessage, authorName, authorEmail,
                        table);
            }

        } catch (MergeConflictsException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to import: " + e.getMessage(), e);
        } finally {

        }

        return importResult;
    }
}
