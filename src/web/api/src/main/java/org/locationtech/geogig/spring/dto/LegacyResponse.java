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

import org.locationtech.geogig.web.api.RESTUtils;
import org.locationtech.geogig.web.api.StreamWriterException;
import org.locationtech.geogig.web.api.StreamingWriter;
import org.springframework.http.MediaType;

/**
 * Base Response class that beans should implement if they wish to use the legacy code to build
 * responses.
 */
public abstract class LegacyResponse {

    /**
     * Encodes this Response to a {@link StreamingWriter} supplied by a Spring controller. The
     * {@link StreamingWriter}s available support XML or JSON output.
     *
     * @param writer  StreamingWriter implementation to which this Response should encode itself.
     * @param format  MediaType to use for the encoding.
     * @param baseUrl
     */
    public abstract void encode(StreamingWriter writer, MediaType format, String baseUrl);

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
