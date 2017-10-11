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
import java.io.Writer;

import org.locationtech.geogig.remote.http.BinaryPackedChanges;
import org.locationtech.geogig.remotes.internal.FilteredDiffIterator;
import org.springframework.http.MediaType;

/**
 *
 */
public class FilteredChanges extends LegacyRepoResponse {

    private BinaryPackedChanges packer;

    private FilteredDiffIterator changes;

    public BinaryPackedChanges getPacker() {
        return packer;
    }

    public FilteredChanges setPacker(BinaryPackedChanges packer) {
        this.packer = packer;
        return this;
    }

    public FilteredDiffIterator getChanges() {
        return changes;
    }

    public FilteredChanges setChanges(FilteredDiffIterator changes) {
        this.changes = changes;
        return this;
    }

    @Override
    protected void encode(Writer out) {
        throw new UnsupportedOperationException(
                "FilteredChanges does not support java.io.Writer. Use java.io.OutputStream instead");
    }

    @Override
    protected void encode(OutputStream out) {
        if (packer != null && changes != null) {
            try {
                packer.write(out, changes);
                // signal the end of changes
                out.write(2);
                if (changes.wasFiltered()) {
                    out.write(1);
                } else {
                    out.write(0);
                }
                changes.close();
            } catch (IOException ioe) {
                throw new RuntimeException (ioe);
            }
        }
    }

    @Override
    public MediaType resolveMediaType(MediaType defaultMediaType) {
        return MediaType.APPLICATION_OCTET_STREAM;
    }


}
