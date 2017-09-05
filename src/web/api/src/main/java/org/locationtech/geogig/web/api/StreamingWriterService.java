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

import org.springframework.http.MediaType;

/**
 * Interface for StreamWriter service loading.
 */
public interface StreamingWriterService {
    public StreamingWriter createWriter(Writer writer) throws StreamWriterException;
    public boolean handles(MediaType mediaType);
}
