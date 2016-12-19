/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.hooks;

public class CannotRunGeogigOperationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CannotRunGeogigOperationException() {
        // default constructor, needed by jdk6
    }

    /**
     * Constructs a new {@code CannotRunGeogigOperationException} with the given message.
     * 
     * @param msg the message for the exception
     */
    public CannotRunGeogigOperationException(String msg) {
        super(msg);
    }

    public CannotRunGeogigOperationException(String message, Throwable cause) {
        super(message, cause);
    }

}
