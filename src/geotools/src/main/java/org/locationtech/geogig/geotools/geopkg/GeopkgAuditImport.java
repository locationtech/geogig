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

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.ProgressListener;
import org.locationtech.geogig.api.RevCommit;
import org.locationtech.geogig.api.porcelain.MergeConflictsException;

import com.google.common.base.Throwables;

public class GeopkgAuditImport extends AbstractGeoGigOp<RevCommit> {

    private String commitMessage;

    private String authorName = null;

    private String authorEmail = null;

    private boolean noCommit = false;

    private File geopackageFile;

    private String table = null;

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
    protected RevCommit _call() throws IllegalArgumentException, IllegalStateException {
        checkArgument(null != geopackageFile, "Geopackage database not provided");
        checkArgument(geopackageFile.exists(), "Database %s does not exist", geopackageFile);
        checkArgument(noCommit || commitMessage != null, "Commit message not provided");
        checkState(workingTree().isClean(),
                "The working tree has unstaged changes. It must be clean for the import to run cleanly.");
        checkState(index().isClean(),
                "The staging ares has uncommitted changes. It must be clean for the import to run cleanly.");

        RevCommit newCommit = null;

        try {
            InterchangeFormat interchange;
            ProgressListener progress = getProgressListener();
            interchange = new InterchangeFormat(geopackageFile, context())
                    .setProgressListener(progress);

            if (table == null) {
                newCommit = interchange.importAuditLog(commitMessage, authorName, authorEmail);
            } else {
                newCommit = interchange.importAuditLog(commitMessage, authorName, authorEmail,
                        table);
            }

        } catch (MergeConflictsException e) {
            Throwables.propagate(e);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to export: " + e.getMessage(), e);
        } finally {

        }

        return newCommit;
    }
}
