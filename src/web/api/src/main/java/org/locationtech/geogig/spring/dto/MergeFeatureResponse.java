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
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.RevFeature;

import com.google.common.base.Throwables;

/**
 *
 */
public class MergeFeatureResponse implements LegacyStatsResponse {

    private RevFeature mergedFeature;

    public RevFeature getMergedFeature() {
        return mergedFeature;
    }

    public MergeFeatureResponse setMergedFeature(RevFeature mergedFeature) {
        this.mergedFeature = mergedFeature;
        return this;
    }

    @Override
    public void encode(OutputStream out) {
        if (mergedFeature != null) {
            final ObjectId oid = mergedFeature.getId();
            if (oid != null) {
                try(OutputStreamWriter osw = new OutputStreamWriter(out)) {
                    osw.write(oid.toString());
                } catch (IOException ioe) {
                    Throwables.propagate(ioe);
                }
            }
        }
    }

}
