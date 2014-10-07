/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.restlet.data.MediaType;

class SimpleAsyncCommandRepresentation<T> extends AsyncCommandRepresentation<T> {

    SimpleAsyncCommandRepresentation(MediaType mediaType, AsyncCommand<T> c, String baseURL) {
        super(mediaType, c, baseURL);
    }

    @Override
    protected void writeResult(XMLStreamWriter w, Object result) throws XMLStreamException {
        // do nothing
    }

    @Override
    protected void writeResultBody(XMLStreamWriter w, Object result) throws XMLStreamException {
        // do nothing
    }
}