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

import org.locationtech.geogig.porcelain.MergeConflictsException;

/**
 * During a geopackage import, a merge may happen after the import occurs. This exception provides
 * the import results in the case of a conflict during the merge.
 */
public class GeopkgMergeConflictsException extends MergeConflictsException
        implements AutoCloseable {

    private static final long serialVersionUID = 1L;

    public final GeopkgImportResult importResult;

    public GeopkgMergeConflictsException(MergeConflictsException e,
            GeopkgImportResult importResult) {

        super(e.getMessage(), e.getOurs(), e.getTheirs(), e.getReport());
        super.initCause(e);
        this.importResult = importResult;
    }

    @Override
    public void close() {
        importResult.close();
    }
}
