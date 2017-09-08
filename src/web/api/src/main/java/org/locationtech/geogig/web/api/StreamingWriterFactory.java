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

import java.io.Writer;
import java.util.Iterator;
import java.util.ServiceLoader;

import org.springframework.http.MediaType;

/**
 * Factory for retrieving {@link StreamingWriter} implementations.
 */
public class StreamingWriterFactory {

    public static StreamingWriter getStreamWriter(MediaType format, Writer parent) {
        final ServiceLoader<StreamingWriterService> svcLoader = ServiceLoader.load(StreamingWriterService.class);
        final Iterator<StreamingWriterService> writerServices = svcLoader.iterator();
        while (writerServices.hasNext()) {
            final StreamingWriterService writerService = writerServices.next();
            if (writerService.handles(format)) {
                return writerService.createWriter(parent);
            }
        }
        // no supported writer found
        throw new IllegalArgumentException("Unsupported MediaType: '" + format + "'");
    }
}
