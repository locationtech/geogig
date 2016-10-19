/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import static org.restlet.data.MediaType.APPLICATION_XML;
import static org.restlet.data.MediaType.TEXT_XML;

import java.io.Writer;

import org.restlet.data.MediaType;

/**
 *
 */
public class XMLStreamingWriterService implements StreamingWriterService {

    @Override
    public StreamingWriter createWriter(Writer writer) throws StreamWriterException {
        return new XMLStreamingWriter(writer);
    }

    @Override
    public boolean handles(MediaType mediaType) {
        return APPLICATION_XML.equals(mediaType) || TEXT_XML.equals(mediaType);
    }

}
