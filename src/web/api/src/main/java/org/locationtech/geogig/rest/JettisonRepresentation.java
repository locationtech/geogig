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

import java.io.IOException;
import java.io.Writer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.codehaus.jettison.mapped.MappedNamespaceConvention;
import org.codehaus.jettison.mapped.MappedXMLStreamWriter;
import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.rest.repository.RESTUtils;
import org.restlet.data.MediaType;

import com.google.common.base.Preconditions;

public abstract class JettisonRepresentation extends WriterRepresentation {

    protected String baseURL;

    public JettisonRepresentation(MediaType mediaType, String baseURL) {
        super(mediaType);
        Preconditions.checkNotNull(mediaType);
        Preconditions.checkNotNull(baseURL);
        this.baseURL = baseURL;
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
        try {
            stax = createWriter(writer);
            stax.writeStartDocument();
            write(stax);
            stax.writeEndDocument();
            stax.flush();
            stax.close();
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    protected abstract void write(XMLStreamWriter stax) throws XMLStreamException;

    protected void encodeAlternateAtomLink(XMLStreamWriter w, String link)
            throws XMLStreamException {
        MediaType format = getMediaType();
        RESTUtils.encodeAlternateAtomLink(format, w,
                RESTUtils.buildHref(baseURL, link, format));
    }

    protected void element(XMLStreamWriter w, String name, @Nullable String value)
            throws XMLStreamException {
        w.writeStartElement(name);
        if (value != null) {
            w.writeCharacters(value);
        }
        w.writeEndElement();
    }

    protected void element(XMLStreamWriter w, String name, Object value) throws XMLStreamException {
        element(w, name, String.valueOf(value));
    }
}