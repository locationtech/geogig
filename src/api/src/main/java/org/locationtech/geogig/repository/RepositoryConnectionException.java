/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

/**
 * Exception to be thrown when the repository was unable to connect to one of its databases.
 * 
 * @since 1.0
 */
public class RepositoryConnectionException extends Exception {

    private static final long serialVersionUID = -4046351627917194599L;

    /**
     * Constructs a new {@code RepositoryConnectionException} with the provided message.
     * 
     * @param message the exception message
     */
    public RepositoryConnectionException(String message) {
        super(message);
    }
}
