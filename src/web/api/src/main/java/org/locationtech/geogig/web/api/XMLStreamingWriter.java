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

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Implementation of StreamingWriter for writing XML.
 */
class XMLStreamingWriter implements StreamingWriter {

    private final XMLStreamWriter xml;

    XMLStreamingWriter(Writer writer) {
        try {
            xml = XMLOutputFactory.newFactory().createXMLStreamWriter(writer);
        } catch (Exception ex) {
            throw new StreamWriterException("Failed to create XMLStreamingWriter", ex);
        }
    }

    @Override
    public void writeStartDocument() throws StreamWriterException {
        try {
            xml.writeStartDocument();
        } catch (XMLStreamException e) {
            throw new StreamWriterException(e);
        }
    }

    @Override
    public void writeEndDocument() throws StreamWriterException {
        try {
            xml.writeEndDocument();
        } catch (XMLStreamException e) {
            throw new StreamWriterException(e);
        }
    }

    @Override
    public void writeStartElement(String name) throws StreamWriterException {
        try {
            xml.writeStartElement(name);
        } catch (XMLStreamException e) {
            throw new StreamWriterException(e);
        }
    }

    @Override
    public void writeEndElement() throws StreamWriterException {
        try {
            xml.writeEndElement();
        } catch (XMLStreamException e) {
            throw new StreamWriterException(e);
        }
    }

    @Override
    public void writeElement(String name, Object value) throws StreamWriterException {
        writeStartElement(name);
        if (value != null) {
            String valueToWrite = value.toString();
            try {
                // try to parse out numeric values
                if (Number.class.isAssignableFrom(value.getClass())) {
                    try {
                        final BigInteger bigInt = new BigInteger(valueToWrite);
                        valueToWrite = bigInt.toString();
                    } catch (NumberFormatException nfe) {
                        // not an Integer, try Decimal
                        try {
                            final BigDecimal bigDecimal = new BigDecimal(valueToWrite);
                            valueToWrite = bigDecimal.toPlainString();
                        } catch (NumberFormatException nfe2) {
                            // just use the string
                        }
                    }
                }
                xml.writeCharacters(valueToWrite);
            } catch (XMLStreamException e) {
                throw new StreamWriterException(e);
            }
        }
        writeEndElement();
    }

    @Override
    public void writeCDataElement(String name, Object value) throws StreamWriterException {
        writeStartElement(name);
        if (value != null) {
            try {
                    xml.writeCData(value.toString());
            } catch (XMLStreamException e) {
                throw new StreamWriterException(e);
            }
        }
        writeEndElement();
    }

    @Override
    public void writeStartArray(String name) throws StreamWriterException {
        // for XML, Arrays are just repeated elements, so no need to start the array here. It should be started in the
        // looping calls to writeStartArrayElement or writeArrayElement.
    }

    @Override
    public void writeEndArray() throws StreamWriterException {
        // for XML, Arrays are just repeated elements, so no need to end the array here. It should be ended in the
        // looping calls to writeStartArrayElement or writeArrayElement.
    }

    @Override
    public void writeStartArrayElement(String name) {
        writeStartElement(name);
    }

    @Override
    public void writeEndArrayElement() {
        writeEndElement();
    }

    @Override
    public void writeArrayElement(String name, Object value) throws StreamWriterException {
        writeElement(name, value);
    }

    @Override
    public void writeCDataArrayElement(String name, Object value) throws StreamWriterException {
        writeCDataElement(name, value);
    }

    @Override
    public void writeAttribute(String name, String value) throws StreamWriterException {
        try {
            xml.writeAttribute(name, value);
        } catch (XMLStreamException e) {
            throw new StreamWriterException(e);
        }
    }

    @Override
    public void close() throws Exception {
        xml.close();
    }

    @Override
    public void flush() throws IOException {
        try {
            xml.flush();
        } catch (XMLStreamException e) {
            throw new IOException(e);
        }
    }

}
