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

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.codehaus.jettison.mapped.MappedNamespaceConvention;
import org.codehaus.jettison.mapped.MappedXMLStreamWriter;
import org.locationtech.geogig.rest.WriterRepresentation;
import org.restlet.data.MediaType;

public class CommandResponseJettisonRepresentation extends WriterRepresentation {

    final CommandResponse impl;

    String callback;

    public CommandResponseJettisonRepresentation(MediaType mediaType, CommandResponse impl,
            String callback) {
        super(mediaType);
        this.impl = impl;
        this.callback = callback;
    }

    private XMLStreamWriter createWriter(Writer writer) {
        final MediaType mediaType = getMediaType();
        XMLStreamWriter xml;
        if (mediaType.getSubType().equalsIgnoreCase("xml")) {
            try {
                xml = XMLOutputFactory.newFactory().createXMLStreamWriter(writer);
            } catch (XMLStreamException ex) {
                throw new RuntimeException(ex);
            }
            callback = null; // this doesn't make sense
        } else if (mediaType == MediaType.APPLICATION_JSON) {
            xml = new MappedXMLStreamWriter(new MappedNamespaceConvention(), writer);
        } else {
            throw new RuntimeException("mediatype not handled " + mediaType);
        }
        return xml;
    }

    @Override
    public void write(Writer writer) throws IOException {
        XMLStreamWriter stax = null;
        if (callback != null) {
            writer.write(callback);
            writer.write('(');
        }
        try {
            stax = createWriter(writer);
            impl.write(new ResponseWriter(stax));
            stax.flush();
            stax.close();
        } catch (Exception ex) {
            throw new IOException(ex);
        }
        if (callback != null) {
            writer.write(");");
        }
    }
}