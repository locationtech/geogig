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

import lombok.Getter;

/**
 * Contains the results of a geopackage import.
 */
public class GeopkgImportResult implements AutoCloseable {
    /**
     * The final commit of the import. If the features were imported on top of an old commit, this
     * would be a merge commit.
     */
    private RevCommit newCommit;

    /**
     * The commit where the features were imported.
     */
    private final @Getter RevCommit importCommit;

    /**
     * Mappings of geopackage feature id to geogig feature id for new features that were added to
     * the repository.
     */
    private final @Getter Map<String, RocksdbMap> newMappings;

    public GeopkgImportResult(RevCommit importCommit) {
        this.importCommit = importCommit;
        this.newMappings = new HashMap<>();
    }

    @Override
    public void close() {
        newMappings.values().forEach(RocksdbMap::close);
        newMappings.clear();
    }

    public RevCommit getNewCommit() {
        return newCommit;
    }

    public void setNewCommit(RevCommit newCommit) {
        this.newCommit = newCommit;
    }
}
