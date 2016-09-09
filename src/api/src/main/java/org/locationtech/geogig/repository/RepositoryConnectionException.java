/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.repository;

public class RepositoryConnectionException extends Exception {

    private static final long serialVersionUID = -4046351627917194599L;

    public RepositoryConnectionException(String message) {
        super(message);
    }
}
