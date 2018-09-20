/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.remotes.pack;

import java.util.Set;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTag;
import org.locationtech.geogig.repository.IndexInfo;

public interface PackBuilder {

    void start(Set<RevTag> tags);

    void startRefResponse(RefRequest req);

    void addCommit(RevCommit commit);

    public void addIndex(IndexInfo indexDef, ObjectId canonicalFeatureTreeId,
            ObjectId oldIndexTreeId, ObjectId newIndexTreeId);

    void endRefResponse();

    Pack build();

}
