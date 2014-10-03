/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest;

import javax.annotation.Nullable;

import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;

/**
 * An exception that specifies the Restlet representation and status code that should be used to
 * report as an HTTP response.
 */
public class RestletException extends RuntimeException {

    private static final long serialVersionUID = -7081583295790313316L;

    private final Status status;

    private final Representation outputRepresentation;

    /**
     * @param message The message to report this error to the user (will report MIME Type as
     *        {@code text/plain})
     * @param status the HTTP status to report
     */
    public RestletException(String message, Status status) {
        this(message, status, null);
    }

    /**
     * @param message The message to report this error to the user (will report MIME Type as
     *        {@code text/plain})
     * @param status the HTTP status to report
     * @param cause the cause of the exception
     */
    public RestletException(String message, Status status, @Nullable Throwable cause) {
        super(cause);
        this.outputRepresentation = new StringRepresentation(message
                + (cause == null ? "" : (":" + cause.getMessage())), MediaType.TEXT_PLAIN);
        this.status = status;
    }

    /**
     * @return the HTTP status code to report
     */
    public Status getStatus() {
        return status;
    }

    /**
     * @return the output representation for this exception
     */
    public Representation getRepresentation() {
        return outputRepresentation;
    }
}
