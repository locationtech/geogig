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

}
