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
import java.io.Writer;

import org.locationtech.geogig.web.api.RESTUtils;
import org.locationtech.geogig.web.api.StreamWriterException;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.locationtech.geogig.web.api.StreamingWriterFactory;
import org.restlet.data.MediaType;

import com.google.common.base.Preconditions;

public abstract class StreamingWriterRepresentation extends WriterRepresentation {

    protected String baseURL;

    public StreamingWriterRepresentation(MediaType mediaType, String baseURL) {
        super(mediaType);
        Preconditions.checkNotNull(mediaType);
        Preconditions.checkNotNull(baseURL);
        this.baseURL = baseURL;
    }

    private StreamingWriter createWriter(Writer writer) {
        return StreamingWriterFactory.getStreamWriter(getMediaType(), writer);
    }

    @Override
    public void write(Writer writer) throws IOException {

        try {
            StreamingWriter streamWriter = createWriter(writer);
            streamWriter.writeStartDocument();
            write(streamWriter);
            streamWriter.writeEndDocument();
            streamWriter.flush();
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    protected abstract void write(StreamingWriter stax) throws StreamWriterException;

    protected void encodeAlternateAtomLink(StreamingWriter w, String link) throws StreamWriterException {
        MediaType format = getMediaType();
        RESTUtils.encodeAlternateAtomLink(format, w,
                RESTUtils.buildHref(baseURL, link, format));
    }
}