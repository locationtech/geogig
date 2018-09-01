/* Copyright (c) 2012-2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.plumbing.remotes;

/**
 * Exception thrown by remote commands.
 * 
 */
@SuppressWarnings("serial")
public class RemoteException extends RuntimeException {

    /**
     * Possible status codes for remote exceptions.
     */
    public enum StatusCode {
        REMOTE_NOT_FOUND, MISSING_NAME, MISSING_URL, REMOTE_ALREADY_EXISTS
    }

    public StatusCode statusCode;

    /**
     * Constructs a new {@code RemoteException} with the gien status code.
     * 
     * @param statusCode the status code for this exception
     */
    public RemoteException(StatusCode statusCode) {
        this(null, statusCode);
    }

    /**
     * Construct a new exception with the given cause and status code.
     * 
     * @param e the cause of this exception
     * @param statusCode the status code for this exception
     */
    public RemoteException(Exception e, StatusCode statusCode) {
        super(String.valueOf(statusCode), e);
        this.statusCode = statusCode;
    }
}
