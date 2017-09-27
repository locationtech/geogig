/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.geotools;

import org.locationtech.geogig.geotools.plumbing.DataStoreImportOp;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.rest.AsyncCommandRepresentation;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.CommandRepresentationFactory;
import org.locationtech.geogig.web.api.ResponseWriter;
import org.locationtech.geogig.web.api.StreamWriterException;
import org.locationtech.geogig.web.api.StreamingWriter;

/**
 * Representation for Commands that produce RevCommit objects (like
 * {@link org.locationtech.geogig.geotools.plumbing.DataStoreImportOp DataStoreImportOp} and
 * {@link org.locationtech.geogig.porcelain.CommitOp CommitOp})
 */
public class DataStoreImportRepresentation extends AsyncCommandRepresentation<RevCommit> {

    public DataStoreImportRepresentation(AsyncContext.AsyncCommand<RevCommit> cmd,
            boolean cleanup) {
        super(cmd, cleanup);
    }

    @Override
    protected void writeResultBody(StreamingWriter w, RevCommit result) throws StreamWriterException {
        if (result != null) {
            ResponseWriter out = new ResponseWriter(w, getMediaType());
            out.writeCommit(result, "commit", null, null, null);
        }
    }

    public static class Factory implements CommandRepresentationFactory<RevCommit> {

        @Override
        public boolean supports(Class<? extends AbstractGeoGigOp<?>> cmdClass) {
            return DataStoreImportOp.class.isAssignableFrom(cmdClass);
        }

        @Override
        public AsyncCommandRepresentation<RevCommit> newRepresentation(
                AsyncContext.AsyncCommand<RevCommit> cmd, boolean cleanup) {
            return new DataStoreImportRepresentation(cmd, cleanup);
        }

    }
}
