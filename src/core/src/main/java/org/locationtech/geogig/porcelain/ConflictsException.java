/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.porcelain;

/**
 * Exception that indicates that an operation cannot be finished due to conflicts
 */
public class ConflictsException extends IllegalStateException{

    private static final long serialVersionUID = 1L;

    public ConflictsException(String msg) {
        super(msg);
    }

}
