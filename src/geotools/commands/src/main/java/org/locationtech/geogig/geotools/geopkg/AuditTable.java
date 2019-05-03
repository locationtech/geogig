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

import org.locationtech.geogig.model.ObjectId;

public class AuditTable {
    private final String tableName, featureTreePath, auditTable;

    private final ObjectId commitId;

    AuditTable(String tableName, String featureTreePath, String auditTable, ObjectId commitId) {
        this.tableName = tableName;
        this.featureTreePath = featureTreePath;
        this.auditTable = auditTable;
        this.commitId = commitId;
    }

    public String getTableName() {
        return tableName;
    }

    public String getFeatureTreePath() {
        return featureTreePath;
    }

    public String getAuditTable() {
        return auditTable;
    }

    public ObjectId getCommitId() {
        return commitId;
    }
}