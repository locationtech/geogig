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

import java.io.Flushable;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Generic Stream Writer interface for writing responses. Implementations include JSON and XML.
 */
public interface StreamingWriter extends AutoCloseable, Flushable {

    /**
     * Writes the beginning of a new Document. Should only be called once.
     */
    public void writeStartDocument() throws StreamWriterException;

    /**
     * Writes the end of a Document. Should only be called once.
     */
    public void writeEndDocument() throws StreamWriterException;

    /**
     * Starts a new Element. This should create a new element under the current element. Use this when the element to be
     * written is complex and has nested elements.
     *
     * @param name The name of the element to write.
     */
    public void writeStartElement(String name) throws StreamWriterException;

    /**
     * Ends the current Element. This will close the currently opened element.
     */
    public void writeEndElement() throws StreamWriterException;

    public void writeElement(String name, @Nullable Object value) throws StreamWriterException;

    public void writeCDataElement(String name, @Nullable Object value) throws StreamWriterException;

    /**
     * Starts a new Array. This is similar to writeStartElement, but starts an array of repeated elements.
     *
     * @param name The name of the element to write
     */
    public void writeStartArray(String name) throws StreamWriterException;

    /**
     * Ends an array.
     */
    public void writeEndArray() throws StreamWriterException;

    public void writeStartArrayElement(String name);

    public void writeEndArrayElement();

    public void writeArrayElement(String name, @Nullable Object value) throws StreamWriterException;

    public void writeCDataArrayElement(String name, @Nullable Object value) throws StreamWriterException;

    public void writeAttribute(String name, String value) throws StreamWriterException;
}
