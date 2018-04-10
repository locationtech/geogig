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

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;

import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.storage.AutoCloseableIterator;

/**
 * AffectedFeatures uses an Iterator, which doesn't map to a JAXB marshaled element well. For now,
 * this response bean won't support XML marshaling.
 */
public class AffectedFeatures extends LegacyRepoResponse {

    private AutoCloseableIterator<DiffEntry> affectedFeatures;

    public AutoCloseableIterator<DiffEntry> getAffectedFeatures() {
        return affectedFeatures;
    }

    public AffectedFeatures setAffectedFeatures(AutoCloseableIterator<DiffEntry> affectedFeatures) {
        this.affectedFeatures = affectedFeatures;
        return this;
    }

    @Override
    protected void encode(OutputStream out) {
        PrintWriter writer = new PrintWriter(out);
        if (affectedFeatures != null) {
            while (affectedFeatures.hasNext()) {
                DiffEntry diffEntry = affectedFeatures.next();
                NodeRef oldObject = diffEntry.getOldObject();
                if (oldObject != null) {
                    writer.print(oldObject.getNode().getObjectId().toString());
                }
            }
            affectedFeatures.close();
        }
        writer.flush();
    }

    @Override
    protected void encode(Writer out) {
        throw new UnsupportedOperationException(
                "AffectedFeatures does not support java.io.Writer. Use java.io.OutputStream instead");
    }

}
