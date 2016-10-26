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

import javax.json.Json;
import javax.json.stream.JsonGenerator;

/**
 *
 */
class JSONStreamingWriter implements StreamingWriter {

    private final JsonGenerator json;

    JSONStreamingWriter(Writer writer) {
        try {
            json = Json.createGenerator(writer);
        } catch (Exception ex) {
            throw new StreamWriterException("Failed to create JSONStreamingWriter", ex);
        }
    }

    @Override
    public void writeStartDocument() throws StreamWriterException {
        json.writeStartObject();
    }

    @Override
    public void writeEndDocument() throws StreamWriterException {
        json.writeEnd();
    }

    @Override
    public void writeStartElement(String name) throws StreamWriterException {
        json.writeStartObject(name);
    }

    @Override
    public void writeEndElement() throws StreamWriterException {
        json.writeEnd();
    }

    @Override
    public void writeElement(String name, Object value) throws StreamWriterException {
        if (value == null || value.toString() == null) {
            // handle nulls first
            json.write(name, (String) null);
            return;
        }
        // figure out if the VALUE is a primitive
        final String valStr = value.toString();
        // try numbers first
        try {
            // hack for NULL ObjectId
            int valInt = Integer.parseInt(valStr);
            if (valInt == 0 && valStr.length() > 1) {
                // treat a bunch of zeros as a String
                json.write(name, valStr);
            } else {
                // treat it as an int
                json.write(name, valInt);
            }
            return;
        } catch (NumberFormatException nfe) {
            // not an Integer
        }
        try {
            json.write(name, Long.parseLong(valStr));
            return;
        } catch (NumberFormatException nfe) {
            // not a Long
        }
        try {
            json.write(name, Float.parseFloat(valStr));
            return;
        } catch (NumberFormatException nfe) {
            // not a Float
        }
        try {
            json.write(name, Double.parseDouble(valStr));
            return;
        } catch (NumberFormatException nfe) {
            // not a Double
        }
        // try boolean
        if ("true".equalsIgnoreCase(valStr)) {
            json.write(name, true);
        } else if ("false".equalsIgnoreCase(valStr)) {
            json.write(name, false);
        } else {
            // just do a string
            json.write(name, valStr);
        }
    }

    @Override
    public void writeLargeElement(String name, Object value) throws StreamWriterException {
        json.write(name, value.toString());
    }

    @Override
    public void writeStartArray(String name) throws StreamWriterException {
        json.writeStartArray(name);
    }

    @Override
    public void writeEndArray() throws StreamWriterException {
        json.writeEnd();
    }

    @Override
    public void writeStartArrayElement(String name) {
        // if we are already in an array, just start a new element/object
        json.writeStartObject();
    }

    @Override
    public void writeEndArrayElement() {
        json.writeEnd();
    }

    @Override
    public void writeArrayElement(String name, Object value) throws StreamWriterException {
        // if we are already in an array, just write the object
        if (value == null || value.toString() == null) {
            // handle nulls first
            json.write((String) null);
            return;
        }
        // figure out if the VALUE is a primitive
        final String valStr = value.toString();
        // try numbers first
        try {
            // hack for NULL ObjectId
            int valInt = Integer.parseInt(valStr);
            if (valInt == 0 && valStr.length() > 1) {
                // treat a bunch of zeros as a String
                json.write(valStr);
            } else {
                // treat it as an int
                json.write(valInt);
            }
            return;
        } catch (NumberFormatException nfe) {
            // not an Integer
        }
        try {
            json.write(Long.parseLong(valStr));
            return;
        } catch (NumberFormatException nfe) {
            // not a Long
        }
        try {
            json.write(Float.parseFloat(valStr));
            return;
        } catch (NumberFormatException nfe) {
            // not a Float
        }
        try {
            json.write(Double.parseDouble(valStr));
            return;
        } catch (NumberFormatException nfe) {
            // not a Double
        }
        // try boolean
        if ("true".equalsIgnoreCase(valStr)) {
            json.write(true);
        } else if ("false".equalsIgnoreCase(valStr)) {
            json.write(false);
        } else {
            // just do a string
            json.write(valStr);
        }
    }

    @Override
    public void writeLargeArrayElement(String name, Object value) throws StreamWriterException {
        // if we are already in an array, just write the object
        json.write(value.toString());
    }

    @Override
    public void writeAttribute(String name, String value) throws StreamWriterException {
        // atributes in JSON are just regular elements
        json.write(name, value);
    }

    @Override
    public void close() throws Exception {
        json.close();
    }

    @Override
    public void flush() throws IOException {
        json.flush();
    }

}
