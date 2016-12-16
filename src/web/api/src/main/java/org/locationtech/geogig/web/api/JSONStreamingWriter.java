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

import javax.json.Json;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerator;

/**
 * Implementation of StreamingWriter for writing JSON.
 */
class JSONStreamingWriter implements StreamingWriter {

    private final JsonGenerator json;

    JSONStreamingWriter(Writer writer) {
        json = Json.createGenerator(writer);
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
        writeElementImpl(name, value, false);
    }

    @Override
    public void writeCDataElement(String name, Object value) throws StreamWriterException {
        writeElement(name, value);
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
        writeElementImpl(name, value, true);
    }

    @Override
    public void writeCDataArrayElement(String name, Object value) throws StreamWriterException {
        // if we are already in an array, just write the object
        writeArrayElement(name, value);
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

    /**
     * Writes the supplied content to the stream. Since the JSON stream writing has separate methods for writing data
     * inside JSON objects as compared to JSON Arrays, we need to branch for each type of write to call the correct
     * method.
     *
     * @param name      The element identifier/tag to write (only used when in the context of a JsonObject).
     * @param value     The value to write (used for both JsonObject and JsonArray contexts).
     * @param isInArray Flag to indicate if the value is being written to a JsonArray context. Should be "true" if the
     *                  context being written to is a JsonArray, "false" if the context is a JsonObject.
     *
     * @throws StreamWriterException Error writing to the output stream.
     */
    private void writeElementImpl(final String name, final Object value, boolean isInArray)
            throws StreamWriterException {
        // check for NULL values first
        if (value == null || value.toString() == null) {
            if (isInArray) {
                // JsonArray context, only write the value
                json.write(JsonValue.NULL);
            } else {
                // non-JsonArray context, write the name and value
                json.write(name, JsonValue.NULL);
            }
            // done handling NULL
            return;
        }
        // handle non-null values
        // figure out if the VALUE is a primitive
        final String valStr = value.toString();
        // try numbers first
        try {
            // hack for NULL ObjectId. A series of zeros (i.e. 000000) will parse as Integer value 0, but we want it as
            // a String explicitly or it won't be written to the JSON stream correclty.
            final BigInteger valInt = new BigInteger(valStr);
            if (valInt.compareTo(BigInteger.ZERO) == 0 && valStr.length() > 1) {
                // treat a bunch of zeros as a String
                if (isInArray) {
                    json.write(valStr);
                } else {
                    json.write(name, valStr);
                }
            } else {
                // treat it as a BigInteger
                if (isInArray) {
                    json.write(valInt);
                } else {
                    json.write(name, valInt);
                }
            }
            // done handling Integer and Long
            return;
        } catch (NumberFormatException nfe) {
            // not an Integer
        }
        try {
            final BigDecimal doubleValue = new BigDecimal(valStr);
            if (isInArray) {
                json.write(doubleValue);
            } else {
                json.write(name, doubleValue);
            }
            // done handling Float and Double
            return;
        } catch (NumberFormatException nfe) {
            // not a Double
        }
        // handle Boolean
        if ("true".equals(valStr)) {
            if (isInArray) {
                json.write(true);
            } else {
                json.write(name, true);
            }
        } else if ("false".equals(valStr)) {
            if (isInArray) {
                json.write(false);
            } else {
                json.write(name, false);
            }
        } else {
            // handle generic String
            if (isInArray) {
                json.write(valStr);
            } else {
                json.write(name, valStr);
            }
        }
    }
}
