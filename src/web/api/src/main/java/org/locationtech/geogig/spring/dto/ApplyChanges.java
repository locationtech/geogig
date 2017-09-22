/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.dto;

import java.io.PrintWriter;
import java.io.Writer;

import org.locationtech.geogig.model.RevCommit;

/**
 *
 */
public class ApplyChanges extends LegacyRepoResponse {

    private RevCommit commit;

    public RevCommit getCommit() {
        return commit;
    }

    public ApplyChanges setCommit(RevCommit commit) {
        this.commit = commit;
        return this;
    }

    @Override
    protected void encode(Writer out) {
        if (commit != null) {
            try (PrintWriter writer = new PrintWriter(out)) {
                writer.print(commit.getId().toString());
            }
        }
    }

}
