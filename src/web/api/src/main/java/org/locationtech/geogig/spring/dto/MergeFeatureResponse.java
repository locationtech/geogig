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

import java.io.IOException;
import java.io.Writer;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;

/**
 *
 */
public class MergeFeatureResponse extends LegacyRepoResponse {

    private RevFeature mergedFeature;

    public RevFeature getMergedFeature() {
        return mergedFeature;
    }

    public MergeFeatureResponse setMergedFeature(RevFeature mergedFeature) {
        this.mergedFeature = mergedFeature;
        return this;
    }

    @Override
    public void encode(Writer out) {
        if (mergedFeature != null) {
            final ObjectId oid = mergedFeature.getId();
            if (oid != null) {
                try {
                    out.write(oid.toString());
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
        }
    }

}
