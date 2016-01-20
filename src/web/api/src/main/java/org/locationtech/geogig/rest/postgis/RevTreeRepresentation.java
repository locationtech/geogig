/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.postgis;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.api.RevTree;
import org.locationtech.geogig.geotools.plumbing.ImportOp;
import org.locationtech.geogig.rest.AsyncCommandRepresentation;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.CommandRepresentationFactory;
import org.restlet.data.MediaType;

/**
 * Representation for commands that return {@link RevTree} (i.e. {@link ImportOp}
 * <p>
 * The SPI factory for this class must be present in
 * {@code META-INF/services/org.locationtech.geogig.rest.CommandRepresentationFactory} as
 * {@code org.locationtech.geogig.rest.postgis.RevTreeRepresentation$Factory}
 *
 */
public class RevTreeRepresentation extends AsyncCommandRepresentation<RevTree> {

    public RevTreeRepresentation(MediaType mediaType, AsyncCommand<RevTree> cmd,
            String baseURL) {
        super(mediaType, cmd, baseURL);
    }

    @Override
    protected void writeResultBody(XMLStreamWriter w, RevTree result)
            throws XMLStreamException {
        if (result != null) {
            w.writeStartElement("RevTree");
            element(w, "treeId", result.getId().toString());
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
                MediaType mediaType, String baseURL) {

            return new RevTreeRepresentation(mediaType, cmd, baseURL);
        }

    }
}