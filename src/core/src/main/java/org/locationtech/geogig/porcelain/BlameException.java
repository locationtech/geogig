/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.porcelain;

/**
 * Exception thrown by BlameOp that contains the error status code.
 * 
 */
@SuppressWarnings("serial")
public class BlameException extends RuntimeException {
    /**
     * Possible status codes for Blame exceptions.
     */
    public enum StatusCode {
        FEATURE_NOT_FOUND, PATH_NOT_FEATURE
    }

    public StatusCode statusCode;

    /**
     * Constructs a new {@code BlameException} with the given status code.
     * 
     * @param statusCode the status code for this exception
     */
    public BlameException(StatusCode statusCode) {
        this(null, statusCode);
    }

    /**
     * Construct a new exception with the given cause and status code.
     * 
     * @param e the cause of this exception
     * @param statusCode the status code for this exception
     */
    public BlameException(Exception e, StatusCode statusCode) {
        super(e);
        this.statusCode = statusCode;
    }
}
