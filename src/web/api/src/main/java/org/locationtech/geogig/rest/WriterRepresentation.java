/* Copyright (c) 2014-2016 Boundless and others.
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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.restlet.data.CharacterSet;
import org.restlet.data.MediaType;
import org.restlet.resource.OutputRepresentation;

/**
 *
 */
public abstract class WriterRepresentation extends OutputRepresentation {

    public WriterRepresentation(MediaType mediaType) {
        super(mediaType);
    }

    @Override
    public void write(OutputStream outputStream) throws IOException {
        Writer writer;

        if (getCharacterSet() != null) {
            writer = new OutputStreamWriter(outputStream, getCharacterSet().getName());
        } else {
            // Use the default HTTP character set
            writer = new OutputStreamWriter(outputStream, CharacterSet.UTF_8.getName());
        }

        write(writer);
        // try to flush and close the writer, if the underlying implementation didn't already
        try {
            writer.flush();
            writer.close();
        } catch (IOException ioe) {
            // ignore it
        }
    }

    /**
     * Implementation specific response write. Implementations should flush and close the Writer.
     * @param writer
     * @throws IOException
     */
    public abstract void write(Writer writer) throws IOException;

}
