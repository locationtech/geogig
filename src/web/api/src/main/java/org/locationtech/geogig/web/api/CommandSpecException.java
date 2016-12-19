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

import org.restlet.data.Status;

/**
 * A user-input (or lack thereof) driven exception. Purposefully does not have a constructor to
 * allow a Throwable cause to be specified.
 */
@SuppressWarnings("serial")
public class CommandSpecException extends IllegalArgumentException {

    private Status status = Status.SERVER_ERROR_INTERNAL;

    /**
     * Constructs a new {code CommandSpecException} with the given message.
     * 
     * @param message the message
     */
    public CommandSpecException(String message) {
        super(message);
    }

    public CommandSpecException(String message, Status status) {
        super(message);
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }

}
