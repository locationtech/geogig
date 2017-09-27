/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rest.geotools;

import org.locationtech.geogig.geotools.plumbing.ImportOp;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.repository.AbstractGeoGigOp;
import org.locationtech.geogig.rest.AsyncCommandRepresentation;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.CommandRepresentationFactory;
import org.locationtech.geogig.web.api.StreamWriterException;
import org.locationtech.geogig.web.api.StreamingWriter;

/**
 * Representation for commands that return {@link RevTree} (i.e. {@link ImportOp}
 * <p>
 * The SPI factory for this class must be present in
 * {@code META-INF/services/org.locationtech.geogig.rest.CommandRepresentationFactory} as
 * {@code org.locationtech.geogig.rest.postgis.RevTreeRepresentation$Factory}
 *
 */
public class ImportRepresentation extends AsyncCommandRepresentation<RevTree> {

    public ImportRepresentation(AsyncCommand<RevTree> cmd, boolean cleanup) {
        super(cmd, cleanup);
    }

    @Override
    protected void writeResultBody(StreamingWriter w, RevTree result)
            throws StreamWriterException {
        if (result != null) {
            w.writeStartElement("RevTree");
            w.writeElement("treeId", result.getId().toString());
            w.writeEndElement();
        }
    }

    public static class Factory implements CommandRepresentationFactory<RevTree> {

        @Override
        public boolean supports(Class<? extends AbstractGeoGigOp<?>> cmdClass) {
            return ImportOp.class.isAssignableFrom(cmdClass);
        }

        @Override
        public AsyncCommandRepresentation<RevTree> newRepresentation(AsyncCommand<RevTree> cmd,
                boolean cleanup) {

            return new ImportRepresentation(cmd, cleanup);
        }

    }
}