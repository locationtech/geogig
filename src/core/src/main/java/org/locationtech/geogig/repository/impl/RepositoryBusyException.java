/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

/**
 * Exception to throw when an operation fails because the repository was too busy.
 */
public class RepositoryBusyException extends RuntimeException {

    private static final long serialVersionUID = 2517358363783618285L;

    /**
     * Construct a new {@code RepositoryBusyException} with the provided message and cause.
     * 
     * @param message the exception message
     * @param cause the root cause of the exception
     */
    public RepositoryBusyException(String message, Exception cause) {
        super(message, cause);
    }

}
