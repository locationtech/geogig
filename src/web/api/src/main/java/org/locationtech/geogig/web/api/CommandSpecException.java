/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.springframework.http.HttpStatus;

/**
 * A user-input (or lack thereof) driven exception. Purposefully does not have a constructor to
 * allow a Throwable cause to be specified.
 */
@SuppressWarnings("serial")
public class CommandSpecException extends IllegalArgumentException {

    private HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

    /**
     * Constructs a new {code CommandSpecException} with the given message.
     * 
     * @param message the message
     */
    public CommandSpecException(String message) {
        super(message);
    }

    public CommandSpecException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    @XmlRootElement(name = "response")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class CommandSpecExceptionResponse {
        @XmlElement
        boolean success = false;

        @XmlElement
        String error;

        public CommandSpecExceptionResponse() {
            this.error = "";
        }

        public CommandSpecExceptionResponse(CommandSpecException ex) {
            this.error = ex.getMessage();
        }
    }
}
