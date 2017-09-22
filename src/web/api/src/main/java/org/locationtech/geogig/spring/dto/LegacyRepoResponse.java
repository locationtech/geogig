/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.spring.dto;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;

import org.locationtech.geogig.web.api.StreamingWriter;
import org.springframework.http.MediaType;

/**
 * Base response class for /repo endpoints. Most of these endpoints return plain text responses.
 */
public abstract class LegacyRepoResponse extends LegacyResponse {

    @Override
    public void encode(Writer writer, MediaType format, String baseUrl) {
        encode(writer);
    }

    @Override
    public MediaType resolveMediaType(MediaType defaultMediaType) {
        return MediaType.TEXT_PLAIN;
    }

    @Override
    protected void encodeInternal(StreamingWriter writer, MediaType format, String baseUrl) {
        // unused
    }

    protected abstract void encode(Writer out);

    /**
     * Encode to a provided OutputStream. This is similar to
     * {@link #encode(java.io.Writer, org.springframework.http.MediaType, java.lang.String)} except
     * that this method provides for direct OutputStream writing. This is to allow responses to
     * reuse encoding logic that does not support writing to Writers.
     * @param outputStream the stream to which to  write this response.
     * @param format output format.
     * @param baseURL Base URL for encoding relative links.
     */
    public final void encode(OutputStream outputStream, MediaType format, String baseURL) {
        encode(outputStream);
    }

    /**
     * Default encoding logic for Repository Command responses. The default implementation is to
     * simply wrap the OutputStream with a PrintWriter and call {@link #encode(java.io.Writer)}.
     * Subclasses that need access to the OutputStream itself should override this.
     * @param out the stream to which to write this response.
     */
    protected void encode(OutputStream out) {
        try (PrintWriter writer = new PrintWriter(out)) {
            encode(writer);
        }
    }
}
