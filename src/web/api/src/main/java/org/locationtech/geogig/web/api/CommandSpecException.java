/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.web.api;

/**
 * A user-input (or lack thereof) driven exception. Purposefully does not have a constructor to
 * allow a Throwable cause to be specified.
 */
@SuppressWarnings("serial")
public class CommandSpecException extends IllegalArgumentException {

    /**
     * Constructs a new {code CommandSpecException} with the given message.
     * 
     * @param message the message
     */
    public CommandSpecException(String message) {
        super(message);
    }

}
