/* Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.io.IOException;
import java.io.Writer;

import org.locationtech.geogig.rest.WriterRepresentation;
import org.restlet.data.MediaType;

public class StreamWriterRepresentation extends WriterRepresentation {

    final StreamResponse impl;

    public StreamWriterRepresentation(MediaType mediaType, StreamResponse impl) {
        super(mediaType);
        this.impl = impl;
    }

    @Override
    public void write(Writer writer) throws IOException {
        try {
            impl.write(writer);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }
}