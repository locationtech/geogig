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

/**
 * Generic Wrapper Exception for StreamWriter exceptions.
 */
public class StreamWriterException extends RuntimeException {

    private static final long serialVersionUID = -3196319765350505927L;

    public StreamWriterException() {
    }

    public StreamWriterException(String msg) {
        super(msg);
    }

    public StreamWriterException(Throwable cause) {
        super(cause);
    }

    public StreamWriterException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
