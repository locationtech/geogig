/*
 *  Copyright (c) 2017 Boundless and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Distribution License v1.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/edl-v10.html
 *
 *  Contributors:
 *  Alex Goudine (Boundless)
 */

package org.locationtech.geogig.porcelain;

public class CRSException extends Exception {
    private static final long serialVersionUID = -4668202156913417181L;

    public CRSException() {

    }

    public CRSException(String message) {
        super(message);
    }

    public CRSException(Throwable cause) {
        super(cause);
    }

    public CRSException(String message, Throwable cause) {
        super(message, cause);
    }
}
