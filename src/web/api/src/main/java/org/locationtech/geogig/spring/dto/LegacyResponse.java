/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.spring.dto;

import java.io.Writer;

import org.locationtech.geogig.web.api.RESTUtils;
import org.locationtech.geogig.web.api.StreamWriterException;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.locationtech.geogig.web.api.StreamingWriterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.google.common.base.Throwables;

/**
 * Base Response class that beans should implement if they wish to use the legacy code to build
 * responses.
 */
public abstract class LegacyResponse {

    /**
     * The {@link HttpStatus} for this response.
     * 
     * @return
     */
    public HttpStatus getStatus() {
        return HttpStatus.OK;
    }

    /**
     * Encodes this Response to a {@link Writer} supplied by a Spring controller.
     * 
     * @param writer the writer to encode the response to
     * @param format {@link MediaType} to use for the encoding
     * @param baseUrl
     */
    public void encode(Writer writer, MediaType format, String baseUrl) {
        try (StreamingWriter streamWriter = StreamingWriterFactory.getStreamWriter(format,
                writer)) {
            streamWriter.writeStartDocument();
            encodeInternal(streamWriter, format, baseUrl);
            streamWriter.writeEndDocument();
        } catch (Exception e) {
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Resolve the MediaType of this response.
     * 
     * @param defaultMediaType the {@link MediaType} suggested by a Spring controller
     * @return the {@link MediaType} to use for the response
     */
    public MediaType resolveMediaType(MediaType defaultMediaType) {
        return defaultMediaType;
    }

    /**
     * Encodes this Response to a {@link StreamingWriter}. The {@link StreamingWriter}s available
     * support XML or JSON output.
     *
     * @param writer StreamingWriter implementation to which this Response should encode itself.
     * @param format MediaType to use for the encoding.
     * @param baseUrl
     */
    protected abstract void encodeInternal(StreamingWriter writer, MediaType format,
            String baseUrl);

    /**
     * Encodes an Atom link element into a response.
     *
     * @param writer  StreamingWriter wrapper around the HTTP OutputStream to which to encode the
     *                link.
     * @param baseURL Base URL of the link to build for writing to the provided StreamingWriter.
     * @param link    The relative link portion to write.
     * @param format  The output format (XML or JSON).
     *
     * @throws StreamWriterException
     */
    protected void encodeAlternateAtomLink(StreamingWriter writer, String baseURL, String link,
            MediaType format) throws StreamWriterException {
        RESTUtils.encodeAlternateAtomLink(format, writer,
                RESTUtils.buildHref(baseURL, link, format));
    }
}
