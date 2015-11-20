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

import org.locationtech.geogig.api.ObjectId;

public class AuditTable {
    private final String tableName, featureTreePath, auditTable;

    private final ObjectId rootTreeId;

    AuditTable(String tableName, String featureTreePath, String auditTable, ObjectId rootTree) {
        this.tableName = tableName;
        this.featureTreePath = featureTreePath;
        this.auditTable = auditTable;
        rootTreeId = rootTree;
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

    public ObjectId getRootTreeId() {
        return rootTreeId;
    }
}