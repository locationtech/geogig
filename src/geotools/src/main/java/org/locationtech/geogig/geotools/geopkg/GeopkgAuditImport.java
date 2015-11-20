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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.File;
import java.util.List;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.porcelain.AddOp;
import org.locationtech.geogig.api.porcelain.CommitOp;
import org.locationtech.geogig.api.porcelain.NothingToCommitException;

public class GeopkgAuditImport extends AbstractGeoGigOp<List<AuditReport>> {

    private String commitMessage;

    private boolean noCommit = false;

    private File geopackageFile;

    public GeopkgAuditImport setDatabase(File geopackageFile) {
        this.geopackageFile = geopackageFile;
        return this;
    }

    /**
     * @param noCommit if {@code true}, do not create a commit from the audit log, just import to
     *        WORK_HEAD; defaults to {@code false}
     */
    public GeopkgAuditImport setNoCommit(boolean noCommit) {
        this.noCommit = noCommit;
        return this;
    }

    public GeopkgAuditImport setCommitMessage(String message) {
        this.commitMessage = message;
        return this;
    }

    @Override
    protected List<AuditReport> _call() throws IllegalArgumentException, IllegalStateException {
        checkArgument(null != geopackageFile, "Geopackage database not provided");
        checkArgument(geopackageFile.exists(), "Database %s does not exist", geopackageFile);
        checkArgument(noCommit || commitMessage != null, "Commit message not provided");
        checkState(workingTree().isClean(),
                "The working tree has unstaged changes. It must be clean for the import to run cleanly.");
        checkState(index().isClean(),
                "The staging ares has uncommitted changes. It must be clean for the import to run cleanly.");

        List<AuditReport> tableReports;

        try {
            InterchangeFormat interchange;
            ProgressListener progress = getProgressListener();
            interchange = new InterchangeFormat(geopackageFile, repository())
                    .setProgressListener(progress);

            tableReports = interchange.importAuditLog();

            if (!noCommit) {
                progress.setDescription("Committing changes...");
                command(AddOp.class).call();
                try {
                    command(CommitOp.class).setMessage(commitMessage).call();
                } catch (NothingToCommitException e) {
                    progress.setDescription(e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to export: " + e.getMessage(), e);
        } finally {

        }

        return tableReports;
    }
}
