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

import java.util.HashMap;
import java.util.Map;

import org.locationtech.geogig.model.RevCommit;

/**
 * Contains the results of a geopackage import.
 */
public class GeopkgImportResult {
    /**
     * The final commit of the import. If the features were imported on top of an old commit, this
     * would be a merge commit.
     */
    public RevCommit newCommit = null;

    /**
     * The commit where the features were imported.
     */
    public final RevCommit importCommit;

    /**
     * Mappings of geopackage feature id to geogig feature id for new features that were added to
     * the repository.
     */
    public final Map<String, Map<String, String>> newMappings;

    public GeopkgImportResult(RevCommit importCommit) {
        this.importCommit = importCommit;
        this.newMappings = new HashMap<String, Map<String, String>>();
    }
}
